import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.0";
import { Mistral } from "npm:@mistralai/mistralai";

const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

const MODEL_NAME = Deno.env.get("CHAT_MODEL")?.trim() || Deno.env.get("WORK_MODEL")?.trim() || "mistral-small-latest";
const FALLBACK_MODELS = [
  MODEL_NAME,
  "open-mistral-nemo",
  "mistral-small-latest",
  "mistral-medium-latest",
].filter((v, i, a) => v && a.indexOf(v) === i);

function getAdminClient() {
  const url = Deno.env.get("SUPABASE_URL") ?? "";
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  return createClient(url, key);
}

function isArabicText(text: string): boolean {
  return /[\u0600-\u06FF]/.test(text);
}

function deriveCleanTitle(userMsg: string, assistantMsg: string): string {
  const cleaned = userMsg
    .replace(/[^\p{L}\p{N}\s]/gu, " ")
    .replace(/\s+/g, " ")
    .trim();
  const words = cleaned.split(" ").filter(Boolean);
  if (words.length >= 2) {
    return words.slice(0, 5).join(" ").slice(0, 48);
  }
  const ansWords = assistantMsg
    .replace(/[^\p{L}\p{N}\s]/gu, " ")
    .replace(/\s+/g, " ")
    .trim()
    .split(" ")
    .filter(Boolean);
  if (isArabicText(userMsg) || isArabicText(assistantMsg)) {
    if (words.length === 1 && /^(هلا|اهلا|أهلا|مرحبا|السلام|سلام|صباح|مساء)$/.test(words[0])) {
      return "ترحيب وبدء المحادثة";
    }
    return words.length === 1
      ? (ansWords.slice(0, 4).join(" ").slice(0, 44) || words[0])
      : (ansWords.slice(0, 4).join(" ").slice(0, 44) || "محادثة جديدة");
  }
  return ansWords.slice(0, 4).join(" ").slice(0, 48) || cleaned || "New conversation";
}

function renderSharedConversationHtml(title: string, messages: Array<{ role: string; content: string; created_at: string }>): string {
  const escapedTitle = title.replace(/</g, "&lt;").replace(/>/g, "&gt;");
  const rows = messages
    .map((m) => {
      const safe = (m.content || "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/\n/g, "<br/>");
      const dir = isArabicText(m.content || "") ? "rtl" : "ltr";
      if (m.role === "user") {
        return `<div style="display:flex;justify-content:flex-end;margin:12px 0;"><div dir="${dir}" style="background:#F1F3F0;color:#0A0A0A;padding:10px 16px;border-radius:22px;max-width:78%;font-size:16px;line-height:22px;">${safe}</div></div>`;
      }
      return `<div dir="${dir}" style="color:#0A0A0A;margin:16px 0;font-size:16px;line-height:24px;">${safe}</div>`;
    })
    .join("\n");

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>${escapedTitle} - Build X</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Plus Jakarta Sans", "Noto Sans Arabic", sans-serif; background: #FFFFFF; color: #0A0A0A; margin: 0; padding: 24px 16px; }
    .container { max-width: 640px; margin: 0 auto; }
    .header { display: flex; align-items: center; justify-content: space-between; padding-bottom: 16px; border-bottom: 1px solid #EFEFEF; margin-bottom: 20px; }
    .brand { font-weight: 700; font-size: 18px; color: #0A0A0A; }
    .badge { background: #F1F3F0; color: #6E7274; font-size: 12px; font-weight: 600; padding: 4px 10px; border-radius: 12px; }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <div class="brand">${escapedTitle}</div>
      <div class="badge">Shared from Build X</div>
    </div>
    ${rows}
  </div>
</body>
</html>`;
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: CORS_HEADERS });
  }

  const url = new URL(req.url);

  // Public read-only shared link endpoint: GET /functions/v1/chat?share_token=...
  if (req.method === "GET") {
    const shareToken = url.searchParams.get("share_token");
    if (!shareToken) {
      return new Response(JSON.stringify({ error: "Missing share_token" }), {
        status: 400,
        headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
      });
    }
    const admin = getAdminClient();
    const { data: conv } = await admin
      .from("conversations")
      .select("id, title, created_at")
      .eq("share_token", shareToken)
      .maybeSingle();

    if (!conv) {
      return new Response(JSON.stringify({ error: "Shared conversation not found or link revoked" }), {
        status: 404,
        headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
      });
    }

    const { data: msgs } = await admin
      .from("messages")
      .select("role, content, created_at")
      .eq("conversation_id", conv.id)
      .order("created_at", { ascending: true });

    const format = url.searchParams.get("format");
    if (format === "json") {
      return new Response(JSON.stringify({ conversation: conv, messages: msgs ?? [] }), {
        status: 200,
        headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
      });
    }

    return new Response(renderSharedConversationHtml(conv.title || "Build X", msgs ?? []), {
      status: 200,
      headers: { ...CORS_HEADERS, "Content-Type": "text/html; charset=utf-8" },
    });
  }

  // POST /functions/v1/chat — requires valid Supabase JWT
  const authHeader = req.headers.get("Authorization");
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    return new Response(JSON.stringify({ error: "Missing Authorization header" }), {
      status: 401,
      headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });
  }

  const admin = getAdminClient();
  const token = authHeader.replace(/^Bearer\s+/i, "").trim();
  const { data: authData, error: authError } = await admin.auth.getUser(token);
  const user = authData?.user;
  if (authError || !user) {
    return new Response(JSON.stringify({ error: "Invalid or expired JWT", details: authError?.message }), {
      status: 401,
      headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });
  }

  let body: any;
  try {
    body = await req.json();
  } catch (_) {
    return new Response(JSON.stringify({ error: "Invalid JSON body" }), {
      status: 400,
      headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });
  }

  const incognito = body.incognito === true;
  const mode: "thinking" | "instant" = body.mode === "instant" ? "instant" : "thinking";
  const inputMessages: Array<{ role: string; content: string }> = Array.isArray(body.messages) ? body.messages : [];
  const attachmentSummaries: Array<{ path: string; mime: string; size?: number }> = Array.isArray(body.attachments) ? body.attachments : [];

  if (inputMessages.length === 0) {
    return new Response(JSON.stringify({ error: "messages array is required" }), {
      status: 400,
      headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });
  }

  const lastUserMsg = inputMessages[inputMessages.length - 1]?.content || "";
  const isArabic = isArabicText(lastUserMsg);

  let conversationId: string | null = body.conversation_id || null;
  let userMessageId: string | null = body.user_message_id || null;
  let assistantMessageId: string | null = body.assistant_message_id || null;
  let isFirstTurn = false;

  // Persist user message and initial streaming assistant message ONLY when NOT incognito
  if (!incognito) {
    if (!conversationId) {
      isFirstTurn = true;
      const { data: createdConv, error: convErr } = await admin
        .from("conversations")
        .insert({
          user_id: user.id,
          title: "Build X",
          pinned: false,
          last_read_at: new Date().toISOString(),
        })
        .select("id")
        .single();
      if (convErr || !createdConv) {
        return new Response(JSON.stringify({ error: convErr?.message || "Failed to create conversation" }), {
          status: 500,
          headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
        });
      }
      conversationId = createdConv.id;
    } else {
      const { count } = await admin
        .from("messages")
        .select("id", { count: "exact", head: true })
        .eq("conversation_id", conversationId);
      isFirstTurn = (count ?? 0) === 0;
    }

    if (!userMessageId) {
      const { data: uRow } = await admin
        .from("messages")
        .insert({
          conversation_id: conversationId,
          user_id: user.id,
          role: "user",
          content: lastUserMsg,
          status: "done",
          mode,
          reasoning: [],
        })
        .select("id")
        .single();
      userMessageId = uRow?.id || null;
    }

    if (userMessageId && attachmentSummaries.length > 0) {
      const attRows = attachmentSummaries.map((a) => ({
        message_id: userMessageId,
        user_id: user.id,
        path: a.path,
        mime: a.mime || "application/octet-stream",
        size: a.size || 0,
      }));
      await admin.from("attachments").insert(attRows);
    }

    if (!assistantMessageId) {
      const { data: aRow } = await admin
        .from("messages")
        .insert({
          conversation_id: conversationId,
          user_id: user.id,
          role: "assistant",
          content: "",
          status: "streaming",
          mode,
          reasoning: [],
        })
        .select("id")
        .single();
      assistantMessageId = aRow?.id || null;
    } else {
      await admin
        .from("messages")
        .update({ content: "", status: "streaming", mode, reasoning: [] })
        .eq("id", assistantMessageId);
    }
  }

  const mistralKey = Deno.env.get("MISTRAL_API_KEY") ?? "";
  const mistral = new Mistral({ apiKey: mistralKey });
  const encoder = new TextEncoder();

  function sseEvent(event: string, data: Record<string, unknown>): Uint8Array {
    return encoder.encode(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
  }

  const readable = new ReadableStream({
    async start(controller) {
      const reasoningSteps: Array<{ title: string; summary: string }> = [];
      let fullAnswer = "";
      let cancelled = false;

      req.signal.addEventListener("abort", async () => {
        cancelled = true;
        if (!incognito && assistantMessageId) {
          await admin
            .from("messages")
            .update({
              content: fullAnswer,
              status: "stopped",
              reasoning: reasoningSteps,
            })
            .eq("id", assistantMessageId);
        }
      });

      try {
        // 1. Emit reasoning steps when mode == "thinking" (localized to Arabic when user writes in Arabic)
        if (mode === "thinking") {
          const step1 = isArabic
            ? {
                title: "تحليل الرسالة والسياق",
                summary: `فهم طلب المستخدم "${lastUserMsg.slice(0, 40)}" وتجهيز إجابة دقيقة باللغة العربية.`,
              }
            : {
                title: "Analyzing request",
                summary: `Understanding the user's prompt "${lastUserMsg.slice(0, 45)}" and structuring a clear response.`,
              };
          reasoningSteps.push(step1);
          controller.enqueue(
            sseEvent("reasoning_step", {
              step_index: 0,
              title: step1.title,
              summary: step1.summary,
              conversation_id: conversationId,
              message_id: assistantMessageId,
            }),
          );

          const step2 = isArabic
            ? {
                title: "صياغة الإجابة وتنظيمها",
                summary: "ترتيب الأفكار ومراجعة المعلومات لتقديم رد واضح ومتكامل باللغة العربية.",
              }
            : {
                title: "Synthesizing detailed answer",
                summary: "Organizing key points and verifying accuracy before streaming the final answer.",
              };
          reasoningSteps.push(step2);
          controller.enqueue(
            sseEvent("reasoning_step", {
              step_index: 1,
              title: step2.title,
              summary: step2.summary,
              conversation_id: conversationId,
              message_id: assistantMessageId,
            }),
          );
          if (!incognito && assistantMessageId) {
            await admin
              .from("messages")
              .update({ reasoning: reasoningSteps })
              .eq("id", assistantMessageId);
          }
        }

        // 2. Stream the assistant answer (single upstream LLM stream per turn)
        const systemPrompt =
          mode === "thinking"
            ? "You are Build X, a helpful, concise, and intelligent AI assistant. Respond in the same language as the user's message (e.g., natural, fluent Arabic if the user writes in Arabic, English if in English). Never use emojis."
            : "You are Build X, a fast and direct AI assistant. Give a clear, immediate answer in the user's language without emojis.";

        const attachmentNote =
          attachmentSummaries.length > 0
            ? `\n\n[User attached ${attachmentSummaries.length} file(s): ${attachmentSummaries.map((a) => `${a.path.split("/").pop()} (${a.mime})`).join(", ")}]`
            : "";

        const llmMessages = [
          { role: "system", content: systemPrompt },
          ...inputMessages.map((m, idx) => ({
            role: m.role === "assistant" ? "assistant" : "user",
            content: idx === inputMessages.length - 1 ? m.content + attachmentNote : m.content,
          })),
        ];

        let streamSuccess = false;
        let lastErr: any = null;

        for (let attempt = 0; attempt < FALLBACK_MODELS.length; attempt++) {
          if (cancelled) break;
          const modelCandidate = FALLBACK_MODELS[attempt];
          try {
            const chatStream = await mistral.chat.stream({
              model: modelCandidate,
              messages: llmMessages as any,
              temperature: mode === "thinking" ? 0.5 : 0.3,
              maxTokens: 2048,
            });

            let chunkCounter = 0;
            for await (const event of chatStream) {
              if (cancelled || req.signal.aborted) break;
              const delta = (event as any)?.data?.choices?.[0]?.delta?.content;
              if (typeof delta === "string" && delta.length > 0) {
                fullAnswer += delta;
                chunkCounter++;
                controller.enqueue(
                  sseEvent("text_delta", {
                    delta,
                    conversation_id: conversationId,
                    message_id: assistantMessageId,
                  }),
                );
                if (!incognito && assistantMessageId && chunkCounter % 8 === 0) {
                  await admin
                    .from("messages")
                    .update({ content: fullAnswer, status: "streaming" })
                    .eq("id", assistantMessageId);
                }
              }
            }
            streamSuccess = true;
            break;
          } catch (err) {
            lastErr = err;
            await new Promise((r) => setTimeout(r, 2500));
          }
        }

        if (!streamSuccess && !cancelled) {
          throw lastErr || new Error("Upstream model failed");
        }

        if (cancelled || req.signal.aborted) {
          if (!incognito && assistantMessageId) {
            await admin
              .from("messages")
              .update({
                content: fullAnswer,
                status: "stopped",
                reasoning: reasoningSteps,
              })
              .eq("id", assistantMessageId);
          }
          controller.close();
          return;
        }

        // 3. Finalize message in database when not incognito
        if (!incognito && assistantMessageId && conversationId) {
          await admin
            .from("messages")
            .update({
              content: fullAnswer,
              status: "done",
              reasoning: reasoningSteps,
            })
            .eq("id", assistantMessageId);

          await admin
            .from("conversations")
            .update({ updated_at: new Date().toISOString() })
            .eq("id", conversationId);
        }

        // 4. Generate conversation title on first turn
        let generatedTitle = "";
        if (isFirstTurn && !incognito && conversationId) {
          generatedTitle = deriveCleanTitle(lastUserMsg, fullAnswer);
          await admin
            .from("conversations")
            .update({ title: generatedTitle, updated_at: new Date().toISOString() })
            .eq("id", conversationId);

          controller.enqueue(
            sseEvent("title", {
              title: generatedTitle,
              conversation_id: conversationId,
            }),
          );
        }

        // 5. Emit done event
        controller.enqueue(
          sseEvent("done", {
            conversation_id: conversationId,
            user_message_id: userMessageId,
            message_id: assistantMessageId,
            status: "done",
            title: generatedTitle || undefined,
          }),
        );
        controller.close();
      } catch (err: any) {
        const errMsg = String(err?.message || "Failed to generate response");
        if (!incognito && assistantMessageId) {
          await admin
            .from("messages")
            .update({
              content: fullAnswer,
              status: "error",
              reasoning: reasoningSteps,
            })
            .eq("id", assistantMessageId);
        }
        controller.enqueue(
          sseEvent("error", {
            error: errMsg,
            conversation_id: conversationId,
            message_id: assistantMessageId,
          }),
        );
        controller.close();
      }
    },
  });

  return new Response(readable, {
    status: 200,
    headers: {
      ...CORS_HEADERS,
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      "Connection": "keep-alive",
    },
  });
});
