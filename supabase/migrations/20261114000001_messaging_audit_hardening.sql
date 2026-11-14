-- ============================================================================
-- Migration: 20261114000001_messaging_audit_hardening.sql
-- Description:
--   Comprehensive SQL, RLS, Grants, Index, Trigger & RPC hardening for the
--   Messaging / Chat System (DMs, Groups, Public AI Chat, Read Receipts, Pins,
--   Mutes, Replies, Realtime, and Push Registration).
-- ============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. Schema Alignment & Constraints
-- ---------------------------------------------------------------------------
ALTER TABLE public.conversations
    ADD COLUMN IF NOT EXISTS is_pinned boolean NOT NULL DEFAULT false;

UPDATE public.conversations
SET is_pinned = pinned
WHERE is_pinned IS DISTINCT FROM pinned;

CREATE OR REPLACE FUNCTION public.trg_conversations_sync_pin()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.pinned := COALESCE(NEW.pinned, false) OR COALESCE(NEW.is_pinned, false);
        NEW.is_pinned := NEW.pinned;
    ELSIF TG_OP = 'UPDATE' THEN
        IF NEW.is_pinned IS DISTINCT FROM OLD.is_pinned AND NEW.pinned IS NOT DISTINCT FROM OLD.pinned THEN
            NEW.pinned := NEW.is_pinned;
        ELSIF NEW.pinned IS DISTINCT FROM OLD.pinned AND NEW.is_pinned IS NOT DISTINCT FROM OLD.is_pinned THEN
            NEW.is_pinned := NEW.pinned;
        ELSE
            NEW.is_pinned := NEW.pinned;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_conversations_sync_pin ON public.conversations;
CREATE TRIGGER trg_conversations_sync_pin
    BEFORE INSERT OR UPDATE ON public.conversations
    FOR EACH ROW EXECUTE FUNCTION public.trg_conversations_sync_pin();

ALTER TABLE public.dm_participants
    DROP CONSTRAINT IF EXISTS dm_participants_status_check;

ALTER TABLE public.dm_participants
    ADD CONSTRAINT dm_participants_status_check
    CHECK (status IN ('active', 'request', 'left', 'hidden'));

-- ---------------------------------------------------------------------------
-- 2. Performance & Foreign-Key Covering Indexes
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_dm_conversations_last_message_id
    ON public.dm_conversations (last_message_id)
    WHERE last_message_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_dm_conversations_pinned_message_id
    ON public.dm_conversations (pinned_message_id)
    WHERE pinned_message_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_dm_conversations_last_sender
    ON public.dm_conversations (last_message_sender_id)
    WHERE last_message_sender_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_dm_messages_reply_to_msg
    ON public.dm_messages (reply_to_message_id)
    WHERE reply_to_message_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_dm_messages_reply_to_note
    ON public.dm_messages (reply_to_note_id)
    WHERE reply_to_note_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_dm_messages_conv_unread
    ON public.dm_messages (conversation_id, created_at)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_messages_conv_role_status_created
    ON public.messages (conversation_id, role, status, created_at DESC);

-- ---------------------------------------------------------------------------
-- 3. RLS Performance Hardening (initplan) & Least-Privilege Table Grants
-- ---------------------------------------------------------------------------
DROP POLICY IF EXISTS "conversations_select_own" ON public.conversations;
CREATE POLICY "conversations_select_own" ON public.conversations
    FOR SELECT TO authenticated
    USING ((SELECT auth.uid()) = user_id);

DROP POLICY IF EXISTS "conversations_insert_own" ON public.conversations;
CREATE POLICY "conversations_insert_own" ON public.conversations
    FOR INSERT TO authenticated
    WITH CHECK ((SELECT auth.uid()) = user_id);

DROP POLICY IF EXISTS "conversations_update_own" ON public.conversations;
CREATE POLICY "conversations_update_own" ON public.conversations
    FOR UPDATE TO authenticated
    USING ((SELECT auth.uid()) = user_id)
    WITH CHECK ((SELECT auth.uid()) = user_id);

DROP POLICY IF EXISTS "conversations_delete_own" ON public.conversations;
CREATE POLICY "conversations_delete_own" ON public.conversations
    FOR DELETE TO authenticated
    USING ((SELECT auth.uid()) = user_id);

DROP POLICY IF EXISTS "messages_select_own" ON public.messages;
CREATE POLICY "messages_select_own" ON public.messages
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.conversations c
            WHERE c.id = messages.conversation_id AND c.user_id = (SELECT auth.uid())
        )
    );

DROP POLICY IF EXISTS "messages_insert_own" ON public.messages;
CREATE POLICY "messages_insert_own" ON public.messages
    FOR INSERT TO authenticated
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.conversations c
            WHERE c.id = messages.conversation_id AND c.user_id = (SELECT auth.uid())
        )
    );

DROP POLICY IF EXISTS "messages_update_own" ON public.messages;
CREATE POLICY "messages_update_own" ON public.messages
    FOR UPDATE TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.conversations c
            WHERE c.id = messages.conversation_id AND c.user_id = (SELECT auth.uid())
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.conversations c
            WHERE c.id = messages.conversation_id AND c.user_id = (SELECT auth.uid())
        )
    );

DROP POLICY IF EXISTS "messages_delete_own" ON public.messages;
CREATE POLICY "messages_delete_own" ON public.messages
    FOR DELETE TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.conversations c
            WHERE c.id = messages.conversation_id AND c.user_id = (SELECT auth.uid())
        )
    );

DROP POLICY IF EXISTS "attachments_select_own" ON public.attachments;
CREATE POLICY "attachments_select_own" ON public.attachments
    FOR SELECT TO authenticated
    USING (
        (SELECT auth.uid()) = user_id OR EXISTS (
            SELECT 1 FROM public.messages m
            JOIN public.conversations c ON c.id = m.conversation_id
            WHERE m.id = attachments.message_id AND c.user_id = (SELECT auth.uid())
        )
    );

DROP POLICY IF EXISTS "attachments_insert_own" ON public.attachments;
CREATE POLICY "attachments_insert_own" ON public.attachments
    FOR INSERT TO authenticated
    WITH CHECK ((SELECT auth.uid()) = user_id);

DROP POLICY IF EXISTS "attachments_update_own" ON public.attachments;
CREATE POLICY "attachments_update_own" ON public.attachments
    FOR UPDATE TO authenticated
    USING ((SELECT auth.uid()) = user_id)
    WITH CHECK ((SELECT auth.uid()) = user_id);

DROP POLICY IF EXISTS "attachments_delete_own" ON public.attachments;
CREATE POLICY "attachments_delete_own" ON public.attachments
    FOR DELETE TO authenticated
    USING ((SELECT auth.uid()) = user_id);

-- DM SELECT policies using initplan (SELECT auth.uid())
DROP POLICY IF EXISTS dm_conversations_select ON public.dm_conversations;
CREATE POLICY dm_conversations_select ON public.dm_conversations
    FOR SELECT TO authenticated
    USING (public._is_dm_participant(id, (SELECT auth.uid())));

DROP POLICY IF EXISTS dm_participants_select ON public.dm_participants;
CREATE POLICY dm_participants_select ON public.dm_participants
    FOR SELECT TO authenticated
    USING (public._is_dm_participant(conversation_id, (SELECT auth.uid())));

DROP POLICY IF EXISTS dm_messages_select ON public.dm_messages;
CREATE POLICY dm_messages_select ON public.dm_messages
    FOR SELECT TO authenticated
    USING (public._is_dm_participant(conversation_id, (SELECT auth.uid())));

-- Least-privilege table grants on DM tables (all writes go through SECURITY DEFINER RPCs
-- except the guarded wallpaper columns on dm_participants)
REVOKE ALL ON TABLE public.dm_conversations, public.dm_participants, public.dm_messages
    FROM PUBLIC, anon, authenticated;

GRANT SELECT ON TABLE public.dm_conversations TO authenticated;
GRANT SELECT ON TABLE public.dm_messages TO authenticated;
GRANT SELECT ON TABLE public.dm_participants TO authenticated;
GRANT UPDATE (wallpaper_id, wallpaper_custom_url, wallpaper_dim, wallpaper_blur)
    ON TABLE public.dm_participants TO authenticated;

-- ---------------------------------------------------------------------------
-- 4. Realtime Publication & Replica Identity
-- ---------------------------------------------------------------------------
ALTER TABLE public.dm_conversations REPLICA IDENTITY FULL;
ALTER TABLE public.dm_participants REPLICA IDENTITY FULL;
ALTER TABLE public.dm_messages REPLICA IDENTITY FULL;
ALTER TABLE public.notifications REPLICA IDENTITY FULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'dm_conversations'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.dm_conversations;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'dm_participants'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.dm_participants;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'dm_messages'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.dm_messages;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 5. Sync Trigger: Handle 'media' & 'note_reply' cleanly without double-writes
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.trg_dm_messages_sync_last_message()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_preview_text text;
    v_prev_id uuid;
    v_prev_content text;
    v_prev_type text;
    v_prev_sender uuid;
    v_prev_created timestamptz;
BEGIN
    IF TG_OP = 'INSERT' THEN
        v_preview_text := CASE
            WHEN NEW.deleted_at IS NOT NULL THEN 'Message deleted'
            WHEN NEW.message_type = 'image' THEN 'Photo'
            WHEN NEW.message_type = 'media' AND coalesce(trim(NEW.content), '') = '' THEN '[Media]'
            WHEN NEW.message_type = 'note_reply' AND coalesce(trim(NEW.content), '') = '' THEN '[Note reply]'
            ELSE NEW.content
        END;

        UPDATE public.dm_conversations
        SET last_message_id = NEW.id,
            last_message_text = v_preview_text,
            last_message_sender_id = NEW.sender_id,
            last_message_type = NEW.message_type,
            last_message_at = NEW.created_at,
            updated_at = now()
        WHERE id = NEW.conversation_id;

        UPDATE public.dm_participants
        SET last_activity_at = NEW.created_at,
            status = CASE
                WHEN user_id = NEW.sender_id AND status IN ('hidden', 'request') THEN 'active'
                WHEN status = 'hidden' THEN 'active'
                ELSE status
            END,
            last_read_at = CASE WHEN user_id = NEW.sender_id THEN NEW.created_at ELSE last_read_at END,
            last_read_message_id = CASE WHEN user_id = NEW.sender_id THEN NEW.id ELSE last_read_message_id END,
            marked_unread = CASE WHEN user_id = NEW.sender_id THEN false ELSE marked_unread END
        WHERE conversation_id = NEW.conversation_id
          AND status != 'left';

        RETURN NEW;

    ELSIF TG_OP = 'UPDATE' THEN
        IF NEW.id = (SELECT last_message_id FROM public.dm_conversations WHERE id = NEW.conversation_id) THEN
            v_preview_text := CASE
                WHEN NEW.deleted_at IS NOT NULL THEN 'Message deleted'
                WHEN NEW.message_type = 'image' THEN 'Photo'
                WHEN NEW.message_type = 'media' AND coalesce(trim(NEW.content), '') = '' THEN '[Media]'
                WHEN NEW.message_type = 'note_reply' AND coalesce(trim(NEW.content), '') = '' THEN '[Note reply]'
                ELSE NEW.content
            END;

            UPDATE public.dm_conversations
            SET last_message_text = v_preview_text,
                last_message_type = NEW.message_type,
                updated_at = now()
            WHERE id = NEW.conversation_id;
        END IF;

        RETURN NEW;

    ELSIF TG_OP = 'DELETE' THEN
        IF OLD.id = (SELECT last_message_id FROM public.dm_conversations WHERE id = OLD.conversation_id) THEN
            SELECT id,
                   CASE
                       WHEN deleted_at IS NOT NULL THEN 'Message deleted'
                       WHEN message_type = 'image' THEN 'Photo'
                       WHEN message_type = 'media' AND coalesce(trim(content), '') = '' THEN '[Media]'
                       WHEN message_type = 'note_reply' AND coalesce(trim(content), '') = '' THEN '[Note reply]'
                       ELSE content
                   END,
                   message_type, sender_id, created_at
            INTO v_prev_id, v_prev_content, v_prev_type, v_prev_sender, v_prev_created
            FROM public.dm_messages
            WHERE conversation_id = OLD.conversation_id AND id != OLD.id
            ORDER BY created_at DESC
            LIMIT 1;

            UPDATE public.dm_conversations
            SET last_message_id = v_prev_id,
                last_message_text = v_prev_content,
                last_message_type = v_prev_type,
                last_message_sender_id = v_prev_sender,
                last_message_at = v_prev_created,
                updated_at = now()
            WHERE id = OLD.conversation_id;
        END IF;

        RETURN OLD;
    END IF;

    RETURN NULL;
END;
$$;

-- ---------------------------------------------------------------------------
-- 6. RPC Fixes
-- ---------------------------------------------------------------------------

-- 6.1 get_or_create_direct_conversation: reactivate 'hidden' chats & epoch last_read_at for unread target
CREATE OR REPLACE FUNCTION public.get_or_create_direct_conversation(target_user_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_u1 uuid;
    v_u2 uuid;
    v_conv_id uuid;
    v_target_status text := 'request';
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    IF target_user_id IS NULL OR target_user_id = v_caller THEN
        RETURN jsonb_build_object('success', false, 'error', 'INVALID_TARGET');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = target_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'USER_NOT_FOUND');
    END IF;

    IF public._are_blocked(v_caller, target_user_id) THEN
        RETURN jsonb_build_object('success', false, 'error', 'BLOCKED');
    END IF;

    v_u1 := LEAST(v_caller, target_user_id);
    v_u2 := GREATEST(v_caller, target_user_id);

    PERFORM pg_advisory_xact_lock(hashtext('buildx:dm_direct:' || v_u1::text || ':' || v_u2::text));

    SELECT id INTO v_conv_id
    FROM public.dm_conversations
    WHERE kind = 'direct' AND direct_user1_id = v_u1 AND direct_user2_id = v_u2;

    IF v_conv_id IS NOT NULL THEN
        INSERT INTO public.dm_participants (conversation_id, user_id, role, status)
        VALUES (v_conv_id, v_caller, 'member', 'active')
        ON CONFLICT (conversation_id, user_id)
        DO UPDATE SET status = CASE
            WHEN dm_participants.status IN ('left', 'hidden') THEN 'active'
            ELSE dm_participants.status
        END;

        RETURN jsonb_build_object(
            'success', true,
            'conversation_id', v_conv_id,
            'is_new', false
        );
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.follows
        WHERE follower_id = target_user_id AND following_id = v_caller
    ) THEN
        v_target_status := 'active';
    END IF;

    INSERT INTO public.dm_conversations (
        kind, direct_user1_id, direct_user2_id, created_by
    ) VALUES (
        'direct', v_u1, v_u2, v_caller
    ) RETURNING id INTO v_conv_id;

    INSERT INTO public.dm_participants (conversation_id, user_id, role, status, last_read_at)
    VALUES
        (v_conv_id, v_caller, 'member', 'active', now()),
        (v_conv_id, target_user_id, 'member', v_target_status, '1970-01-01T00:00:00Z'::timestamptz);

    RETURN jsonb_build_object(
        'success', true,
        'conversation_id', v_conv_id,
        'is_new', true
    );
END;
$$;

-- 6.2 create_group_conversation: initialize member last_read_at at epoch so system msg isn't pre-read
CREATE OR REPLACE FUNCTION public.create_group_conversation(
    p_title text,
    p_member_ids uuid[],
    p_avatar_url text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_conv_id uuid;
    v_clean_title text := trim(p_title);
    v_member_id uuid;
    v_added_count int := 0;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    IF v_clean_title IS NULL OR char_length(v_clean_title) < 1 OR char_length(v_clean_title) > 80 THEN
        RETURN jsonb_build_object('success', false, 'error', 'INVALID_TITLE');
    END IF;

    INSERT INTO public.dm_conversations (
        kind, title, avatar_url, created_by
    ) VALUES (
        'group', v_clean_title, p_avatar_url, v_caller
    ) RETURNING id INTO v_conv_id;

    INSERT INTO public.dm_participants (conversation_id, user_id, role, status, last_read_at)
    VALUES (v_conv_id, v_caller, 'admin', 'active', now());

    IF p_member_ids IS NOT NULL THEN
        FOREACH v_member_id IN ARRAY p_member_ids LOOP
            IF v_member_id IS NOT NULL AND v_member_id <> v_caller THEN
                IF EXISTS (SELECT 1 FROM public.profiles WHERE id = v_member_id) AND NOT public._are_blocked(v_caller, v_member_id) THEN
                    INSERT INTO public.dm_participants (conversation_id, user_id, role, status, last_read_at)
                    VALUES (v_conv_id, v_member_id, 'member', 'active', '1970-01-01T00:00:00Z'::timestamptz)
                    ON CONFLICT DO NOTHING;
                    v_added_count := v_added_count + 1;
                END IF;
            END IF;
        END LOOP;
    END IF;

    INSERT INTO public.dm_messages (
        conversation_id, sender_id, message_type, content
    ) VALUES (
        v_conv_id, v_caller, 'system', 'created the group'
    );

    RETURN jsonb_build_object(
        'success', true,
        'conversation_id', v_conv_id,
        'members_added', v_added_count
    );
END;
$$;

-- 6.3 remove_or_leave_group_member: clear pin state and set left_at
CREATE OR REPLACE FUNCTION public.remove_or_leave_group_member(
    p_conversation_id uuid,
    p_target_user_id uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_kind text;
    v_caller_role text;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    SELECT kind INTO v_kind FROM public.dm_conversations WHERE id = p_conversation_id;
    IF v_kind IS NULL OR v_kind <> 'group' THEN
        RETURN jsonb_build_object('success', false, 'error', 'NOT_A_GROUP');
    END IF;

    SELECT role INTO v_caller_role FROM public.dm_participants
    WHERE conversation_id = p_conversation_id AND user_id = v_caller AND status = 'active';

    IF v_caller_role IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'NOT_A_PARTICIPANT');
    END IF;

    IF p_target_user_id = v_caller THEN
        UPDATE public.dm_participants
        SET status = 'left',
            left_at = now(),
            pinned_at = NULL,
            is_pinned = false
        WHERE conversation_id = p_conversation_id AND user_id = v_caller;
        RETURN jsonb_build_object('success', true, 'action', 'left');
    ELSE
        IF v_caller_role <> 'admin' THEN
            RETURN jsonb_build_object('success', false, 'error', 'NOT_ADMIN');
        END IF;

        UPDATE public.dm_participants
        SET status = 'left',
            left_at = now(),
            pinned_at = NULL,
            is_pinned = false
        WHERE conversation_id = p_conversation_id AND user_id = p_target_user_id;
        RETURN jsonb_build_object('success', true, 'action', 'removed');
    END IF;
END;
$$;

-- 6.4 update_group_info: verify group kind & title bounds
CREATE OR REPLACE FUNCTION public.update_group_info(
    p_conversation_id uuid,
    p_title text,
    p_avatar_url text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_clean_title text := trim(coalesce(p_title, ''));
    v_role text;
    v_kind text;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    SELECT kind INTO v_kind FROM public.dm_conversations WHERE id = p_conversation_id;
    IF v_kind IS NULL OR v_kind <> 'group' THEN
        RETURN jsonb_build_object('success', false, 'error', 'NOT_A_GROUP');
    END IF;

    SELECT role INTO v_role
    FROM public.dm_participants
    WHERE conversation_id = p_conversation_id AND user_id = v_caller AND status = 'active';

    IF v_role IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'NOT_A_MEMBER');
    END IF;

    IF char_length(v_clean_title) < 1 OR char_length(v_clean_title) > 80 THEN
        RETURN jsonb_build_object('success', false, 'error', 'INVALID_TITLE');
    END IF;

    UPDATE public.dm_conversations
    SET
        title = v_clean_title,
        avatar_url = coalesce(p_avatar_url, avatar_url),
        updated_at = now()
    WHERE id = p_conversation_id AND kind = 'group';

    RETURN jsonb_build_object('success', true, 'title', v_clean_title);
END;
$$;

-- 6.5 get_dm_inbox: restore status, last_read_at, last_read_message_id, last_message;
--     fix 'requests' filter, keyset cursor pagination, blank sender_first_name, and agg ordering
CREATE OR REPLACE FUNCTION public.get_dm_inbox(
    p_filter text DEFAULT 'all',
    p_search text DEFAULT '',
    p_cursor_pinned timestamptz DEFAULT NULL,
    p_cursor_activity timestamptz DEFAULT NULL,
    p_limit int DEFAULT 40
)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_filter text := CASE
        WHEN lower(trim(coalesce(p_filter, ''))) IN ('unread', 'groups', 'requests')
            THEN lower(trim(p_filter))
        ELSE 'all'
    END;
    v_search text := trim(coalesce(p_search, ''));
    v_result jsonb;
BEGIN
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('conversations', '[]'::jsonb);
    END IF;

    WITH caller_parts AS (
        SELECT
            p.conversation_id,
            p.role,
            p.status,
            p.pinned_at,
            (p.pinned_at IS NOT NULL) AS is_pinned,
            p.muted_until,
            (p.muted_until IS NOT NULL AND p.muted_until > now()) AS is_muted,
            p.marked_unread,
            p.cleared_before,
            p.last_read_at,
            p.last_read_message_id,
            p.last_activity_at
        FROM public.dm_participants p
        WHERE p.user_id = v_user_id
          AND p.status != 'left'
    ),
    valid_convs AS (
        SELECT
            cp.*,
            c.kind,
            c.title,
            c.avatar_url AS group_avatar_url,
            c.last_message_id,
            c.last_message_text,
            c.last_message_sender_id,
            c.last_message_type,
            c.last_message_at,
            c.created_at AS conv_created_at,
            CASE
                WHEN c.kind = 'direct' THEN
                    CASE WHEN c.direct_user1_id = v_user_id THEN c.direct_user2_id ELSE c.direct_user1_id END
                ELSE NULL
            END AS peer_user_id
        FROM caller_parts cp
        JOIN public.dm_conversations c ON c.id = cp.conversation_id
        WHERE (cp.status != 'hidden' OR (c.last_message_at IS NOT NULL AND c.last_message_at > cp.cleared_before))
    ),
    filtered_convs AS (
        SELECT vc.*
        FROM valid_convs vc
        WHERE (
            vc.peer_user_id IS NULL
            OR NOT EXISTS (
                SELECT 1 FROM public.blocks b
                WHERE (b.blocker_id = v_user_id AND b.blocked_id = vc.peer_user_id)
                   OR (b.blocker_id = vc.peer_user_id AND b.blocked_id = v_user_id)
            )
        )
    ),
    enriched_convs AS (
        SELECT
            fc.*,
            prof.display_name AS peer_display_name,
            prof.handle AS peer_handle,
            prof.avatar_url AS peer_avatar_url,
            prof.is_verified AS peer_is_verified,
            CASE
                WHEN fc.last_message_sender_id = v_user_id THEN 'You'
                ELSE COALESCE(
                    NULLIF(trim(split_part(coalesce(sender_prof.display_name, ''), ' ', 1)), ''),
                    NULLIF(trim(sender_prof.username::text), ''),
                    'User'
                )
            END AS sender_first_name,
            CASE
                WHEN fc.last_message_id IS NOT NULL THEN jsonb_build_object(
                    'id', fc.last_message_id,
                    'sender_id', COALESCE(fc.last_message_sender_id, v_user_id),
                    'sender_name', COALESCE(
                        NULLIF(trim(sender_prof.display_name), ''),
                        NULLIF(trim(sender_prof.username::text), ''),
                        'User'
                    ),
                    'message_type', COALESCE(fc.last_message_type, 'text'),
                    'content', COALESCE(fc.last_message_text, ''),
                    'created_at', COALESCE(fc.last_message_at, fc.last_activity_at),
                    'deleted_at', CASE WHEN fc.last_message_text = 'Message deleted' THEN fc.last_message_at ELSE NULL END
                )
                ELSE NULL
            END AS last_message_obj,
            CASE
                WHEN fc.marked_unread THEN GREATEST(1, (
                    SELECT count(*)::int
                    FROM public.dm_messages m
                    WHERE m.conversation_id = fc.conversation_id
                      AND m.sender_id != v_user_id
                      AND m.created_at > fc.last_read_at
                      AND (fc.cleared_before IS NULL OR m.created_at > fc.cleared_before)
                      AND m.deleted_at IS NULL
                ))
                ELSE (
                    SELECT count(*)::int
                    FROM public.dm_messages m
                    WHERE m.conversation_id = fc.conversation_id
                      AND m.sender_id != v_user_id
                      AND m.created_at > fc.last_read_at
                      AND (fc.cleared_before IS NULL OR m.created_at > fc.cleared_before)
                      AND m.deleted_at IS NULL
                )
            END AS unread_count,
            CASE
                WHEN fc.kind = 'group' THEN (
                    SELECT count(*)::int
                    FROM public.dm_participants gp
                    WHERE gp.conversation_id = fc.conversation_id AND gp.status = 'active'
                )
                ELSE 2
            END AS member_count,
            CASE
                WHEN fc.kind = 'group' THEN (
                    SELECT COALESCE(jsonb_agg(
                        jsonb_build_object(
                            'user_id', gp.user_id,
                            'display_name', COALESCE(NULLIF(trim(gprof.display_name), ''), gprof.username::text, 'User'),
                            'avatar_url', gprof.avatar_url
                        )
                        ORDER BY (gprof.avatar_url IS NOT NULL AND gprof.avatar_url <> '') DESC, gp.joined_at ASC
                    ), '[]'::jsonb)
                    FROM (
                        SELECT gp2.user_id, gp2.joined_at
                        FROM public.dm_participants gp2
                        JOIN public.profiles gprof2 ON gprof2.id = gp2.user_id
                        WHERE gp2.conversation_id = fc.conversation_id AND gp2.status = 'active'
                        ORDER BY (gprof2.avatar_url IS NOT NULL AND gprof2.avatar_url <> '') DESC, gp2.joined_at ASC
                        LIMIT 4
                    ) gp
                    JOIN public.profiles gprof ON gprof.id = gp.user_id
                )
                ELSE '[]'::jsonb
            END AS member_avatars
        FROM filtered_convs fc
        LEFT JOIN public.profiles prof ON prof.id = fc.peer_user_id
        LEFT JOIN public.profiles sender_prof ON sender_prof.id = fc.last_message_sender_id
    ),
    tab_filtered AS (
        SELECT *
        FROM enriched_convs ec
        WHERE (
            v_filter = 'all'
            OR (v_filter = 'requests' AND ec.status = 'request')
            OR (v_filter = 'unread' AND ec.unread_count > 0)
            OR (v_filter = 'groups' AND ec.kind = 'group')
        )
        AND (
            v_search = ''
            OR (ec.kind = 'group' AND ec.title ILIKE '%' || v_search || '%')
            OR (ec.peer_display_name ILIKE '%' || v_search || '%')
            OR (ec.peer_handle ILIKE '%' || v_search || '%')
            OR (ec.last_message_text ILIKE '%' || v_search || '%')
        )
    ),
    paginated AS (
        SELECT *
        FROM tab_filtered tf
        WHERE (
            (p_cursor_pinned IS NULL AND p_cursor_activity IS NULL)
            OR (p_cursor_pinned IS NOT NULL AND (
                (tf.is_pinned = true AND tf.pinned_at < p_cursor_pinned)
                OR (tf.is_pinned = false AND (p_cursor_activity IS NULL OR tf.last_activity_at < p_cursor_activity))
            ))
            OR (p_cursor_pinned IS NULL AND p_cursor_activity IS NOT NULL AND (
                tf.is_pinned = false AND tf.last_activity_at < p_cursor_activity
            ))
        )
        ORDER BY tf.is_pinned DESC, tf.pinned_at DESC NULLS LAST, tf.last_activity_at DESC
        LIMIT GREATEST(1, LEAST(coalesce(p_limit, 40), 50))
    )
    SELECT jsonb_build_object(
        'conversations', COALESCE(
            jsonb_agg(
                jsonb_build_object(
                    'id', p.conversation_id,
                    'conversation_id', p.conversation_id,
                    'kind', p.kind,
                    'title', CASE WHEN p.kind = 'group' THEN COALESCE(p.title, 'Group') ELSE COALESCE(p.peer_display_name, p.peer_handle, 'User') END,
                    'display_name', CASE WHEN p.kind = 'group' THEN COALESCE(p.title, 'Group') ELSE COALESCE(p.peer_display_name, p.peer_handle, 'User') END,
                    'handle', p.peer_handle,
                    'avatar_url', CASE WHEN p.kind = 'group' THEN p.group_avatar_url ELSE p.peer_avatar_url END,
                    'status', p.status,
                    'is_verified', COALESCE(p.peer_is_verified, false),
                    'is_pinned', p.is_pinned,
                    'pinned_at', p.pinned_at,
                    'is_muted', p.is_muted,
                    'muted_until', p.muted_until,
                    'is_marked_unread', p.marked_unread,
                    'last_read_at', p.last_read_at,
                    'last_read_message_id', p.last_read_message_id,
                    'unread_count', p.unread_count,
                    'last_message_id', p.last_message_id,
                    'last_message_text', p.last_message_text,
                    'last_message_type', p.last_message_type,
                    'last_message_sender_id', p.last_message_sender_id,
                    'sender_first_name', p.sender_first_name,
                    'last_message_at', p.last_message_at,
                    'last_activity_at', p.last_activity_at,
                    'last_message', p.last_message_obj,
                    'member_count', p.member_count,
                    'member_avatars', p.member_avatars
                )
                ORDER BY p.is_pinned DESC, p.pinned_at DESC NULLS LAST, p.last_activity_at DESC
            ),
            '[]'::jsonb
        )
    ) INTO v_result
    FROM paginated p;

    RETURN v_result;
END;
$$;

-- 6.6 get_dm_conversation_detail: fix pinned_message.conversation_id & dynamic is_muted/is_pinned
CREATE OR REPLACE FUNCTION public.get_dm_conversation_detail(p_conversation_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_conv record;
    v_pinned_obj jsonb := NULL;
    v_participants jsonb;
    v_peer_intro jsonb := NULL;
    v_peer_id uuid;
    v_wp record;
    v_wp_id text := 'default';
    v_wp_custom text := NULL;
    v_wp_dim real := 0.0;
    v_wp_blur boolean := false;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    IF NOT public._is_dm_participant(p_conversation_id, v_caller) THEN
        RETURN jsonb_build_object('success', false, 'error', 'NOT_A_PARTICIPANT');
    END IF;

    SELECT * INTO v_conv FROM public.dm_conversations WHERE id = p_conversation_id;
    IF v_conv.id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'CONVERSATION_NOT_FOUND');
    END IF;

    SELECT
        COALESCE(dp.wallpaper_id, v_conv.wallpaper_id, pr.default_wallpaper_id, 'default') AS resolved_wallpaper_id,
        CASE
            WHEN dp.wallpaper_id IS NOT NULL THEN dp.wallpaper_custom_url
            WHEN v_conv.wallpaper_id IS NOT NULL THEN v_conv.wallpaper_custom_url
            ELSE pr.default_wallpaper_custom_url
        END AS resolved_custom_url,
        CASE
            WHEN dp.wallpaper_id IS NOT NULL THEN dp.wallpaper_dim
            WHEN v_conv.wallpaper_id IS NOT NULL THEN COALESCE(v_conv.wallpaper_dim, 0.0)
            ELSE COALESCE(pr.default_wallpaper_dim, 0.0)
        END AS resolved_dim,
        CASE
            WHEN dp.wallpaper_id IS NOT NULL THEN dp.wallpaper_blur
            WHEN v_conv.wallpaper_id IS NOT NULL THEN COALESCE(v_conv.wallpaper_blur, false)
            ELSE COALESCE(pr.default_wallpaper_blur, false)
        END AS resolved_blur
    INTO v_wp
    FROM public.dm_participants dp
    JOIN public.profiles pr ON pr.id = dp.user_id
    WHERE dp.conversation_id = p_conversation_id
      AND dp.user_id = v_caller;

    IF FOUND THEN
        v_wp_id := COALESCE(v_wp.resolved_wallpaper_id, 'default');
        v_wp_custom := v_wp.resolved_custom_url;
        v_wp_dim := LEAST(GREATEST(COALESCE(v_wp.resolved_dim, 0.0), 0.0), 0.70);
        v_wp_blur := COALESCE(v_wp.resolved_blur, false);
    END IF;

    IF v_conv.pinned_message_id IS NOT NULL THEN
        SELECT jsonb_build_object(
            'id', pm.id,
            'conversation_id', pm.conversation_id,
            'sender_id', pm.sender_id,
            'sender_username', pr.username,
            'sender_handle', pr.handle,
            'sender_display_name', pr.display_name,
            'sender_avatar_url', pr.avatar_url,
            'sender_is_verified', pr.is_verified,
            'message_type', pm.message_type,
            'content', pm.content,
            'attachment_path', pm.attachment_path,
            'attachment_mime', pm.attachment_mime,
            'attachment_size', pm.attachment_size,
            'edited_at', pm.edited_at,
            'created_at', pm.created_at
        ) INTO v_pinned_obj
        FROM public.dm_messages pm
        JOIN public.profiles pr ON pr.id = pm.sender_id
        WHERE pm.id = v_conv.pinned_message_id AND pm.deleted_at IS NULL;
    END IF;

    SELECT coalesce(jsonb_agg(
        jsonb_build_object(
            'user_id', p.user_id,
            'username', pr.username,
            'handle', pr.handle,
            'display_name', pr.display_name,
            'avatar_url', pr.avatar_url,
            'is_verified', pr.is_verified,
            'role', p.role,
            'status', p.status,
            'is_muted', CASE WHEN p.muted_until IS NOT NULL THEN p.muted_until > now() ELSE p.is_muted END,
            'is_pinned', (p.pinned_at IS NOT NULL OR p.is_pinned),
            'last_read_at', p.last_read_at,
            'last_read_message_id', p.last_read_message_id
        )
        ORDER BY p.joined_at ASC
    ), '[]'::jsonb) INTO v_participants
    FROM public.dm_participants p
    JOIN public.profiles pr ON pr.id = p.user_id
    WHERE p.conversation_id = p_conversation_id;

    IF v_conv.kind = 'direct' THEN
        v_peer_id := CASE WHEN v_conv.direct_user1_id = v_caller THEN v_conv.direct_user2_id ELSE v_conv.direct_user1_id END;

        SELECT jsonb_build_object(
            'id', pr.id,
            'username', pr.username,
            'handle', pr.handle,
            'display_name', pr.display_name,
            'avatar_url', pr.avatar_url,
            'is_verified', pr.is_verified,
            'bio', pr.bio,
            'followers_count', pr.followers_count,
            'following_count', pr.following_count,
            'posts_count', COALESCE((SELECT count(*)::int FROM public.attachments a WHERE a.user_id = pr.id), 0),
            'following_since', (SELECT created_at FROM public.follows WHERE follower_id = v_caller AND following_id = pr.id),
            'created_at', pr.created_at
        ) INTO v_peer_intro
        FROM public.profiles pr
        WHERE pr.id = v_peer_id;
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'wallpaper_id', v_wp_id,
        'wallpaper_custom_url', v_wp_custom,
        'wallpaper_dim', v_wp_dim,
        'wallpaper_blur', v_wp_blur,
        'conversation', jsonb_build_object(
            'id', v_conv.id,
            'kind', v_conv.kind,
            'title', v_conv.title,
            'avatar_url', v_conv.avatar_url,
            'pinned_message_id', v_conv.pinned_message_id,
            'created_by', v_conv.created_by,
            'created_at', v_conv.created_at,
            'wallpaper_id', v_wp_id,
            'wallpaper_custom_url', v_wp_custom,
            'wallpaper_dim', v_wp_dim,
            'wallpaper_blur', v_wp_blur
        ),
        'pinned_message', v_pinned_obj,
        'participants', v_participants,
        'peer_intro', v_peer_intro
    );
END;
$$;

-- 6.7 get_dm_messages: respect cleared_before & deterministic jsonb_agg ordering
CREATE OR REPLACE FUNCTION public.get_dm_messages(
    p_conversation_id uuid,
    p_before timestamptz DEFAULT NULL,
    p_limit int DEFAULT 50
)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_cleared_before timestamptz;
    v_result jsonb;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    IF NOT public._is_dm_participant(p_conversation_id, v_caller) THEN
        RAISE EXCEPTION 'NOT_A_PARTICIPANT' USING ERRCODE = '42501';
    END IF;

    SELECT cleared_before INTO v_cleared_before
    FROM public.dm_participants
    WHERE conversation_id = p_conversation_id AND user_id = v_caller;

    WITH raw_messages AS (
        SELECT
            m.id,
            m.conversation_id,
            m.sender_id,
            m.message_type,
            m.content,
            m.reply_to_message_id,
            m.reply_to_note_id,
            m.note_snapshot_text,
            m.attachment_path,
            m.attachment_mime,
            m.attachment_size,
            m.client_temp_id,
            m.edited_at,
            m.deleted_at,
            m.created_at,
            pr.username AS sender_username,
            pr.handle AS sender_handle,
            pr.display_name AS sender_display_name,
            pr.avatar_url AS sender_avatar_url,
            pr.is_verified AS sender_is_verified
        FROM public.dm_messages m
        JOIN public.profiles pr ON pr.id = m.sender_id
        WHERE m.conversation_id = p_conversation_id
          AND (p_before IS NULL OR m.created_at < p_before)
          AND (v_cleared_before IS NULL OR m.created_at > v_cleared_before)
        ORDER BY m.created_at DESC
        LIMIT GREATEST(1, LEAST(coalesce(p_limit, 50), 100))
    ),
    messages_with_reply AS (
        SELECT
            rm.*,
            CASE
                WHEN rm.reply_to_message_id IS NOT NULL THEN (
                    SELECT jsonb_build_object(
                        'id', rep.id,
                        'sender_id', rep.sender_id,
                        'sender_display_name', rpr.display_name,
                        'content', CASE WHEN rep.deleted_at IS NOT NULL THEN 'Message deleted' ELSE rep.content END,
                        'attachment_mime', rep.attachment_mime,
                        'is_deleted', (rep.deleted_at IS NOT NULL)
                    )
                    FROM public.dm_messages rep
                    JOIN public.profiles rpr ON rpr.id = rep.sender_id
                    WHERE rep.id = rm.reply_to_message_id
                      AND rep.conversation_id = p_conversation_id
                )
                ELSE NULL
            END AS reply_to_obj
        FROM raw_messages rm
    )
    SELECT coalesce(jsonb_agg(
        jsonb_build_object(
            'id', id,
            'conversation_id', conversation_id,
            'sender_id', sender_id,
            'sender_username', sender_username,
            'sender_handle', sender_handle,
            'sender_display_name', sender_display_name,
            'sender_avatar_url', sender_avatar_url,
            'sender_is_verified', sender_is_verified,
            'message_type', message_type,
            'content', CASE WHEN deleted_at IS NOT NULL THEN '' ELSE content END,
            'reply_to', reply_to_obj,
            'reply_to_note_id', reply_to_note_id,
            'note_snapshot_text', note_snapshot_text,
            'attachment_path', attachment_path,
            'attachment_mime', attachment_mime,
            'attachment_size', attachment_size,
            'client_temp_id', client_temp_id,
            'edited_at', edited_at,
            'deleted_at', deleted_at,
            'created_at', created_at
        )
        ORDER BY created_at ASC, id ASC
    ), '[]'::jsonb) INTO v_result
    FROM messages_with_reply;

    RETURN v_result;
END;
$$;

-- 6.8 send_dm_message: idempotent client_temp_id, cross-conv reply validation, empty check, no redundant updates
CREATE OR REPLACE FUNCTION public.send_dm_message(
    p_conversation_id uuid,
    p_content text,
    p_reply_to_message_id uuid DEFAULT NULL,
    p_reply_to_note_id uuid DEFAULT NULL,
    p_attachment_path text DEFAULT NULL,
    p_attachment_mime text DEFAULT NULL,
    p_attachment_size bigint DEFAULT NULL,
    p_client_temp_id text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_conv record;
    v_peer_id uuid;
    v_msg_type text := 'text';
    v_clean_content text := coalesce(trim(p_content), '');
    v_clean_temp_id text := NULLIF(trim(coalesce(p_client_temp_id, '')), '');
    v_note_snapshot text := NULL;
    v_msg_id uuid;
    v_created_at timestamptz;
    v_reply_to_obj jsonb := NULL;
    v_sender_prof record;
    v_existing record;
    v_reply_id uuid := p_reply_to_message_id;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    SELECT * INTO v_conv FROM public.dm_conversations WHERE id = p_conversation_id;
    IF v_conv.id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'CONVERSATION_NOT_FOUND');
    END IF;

    IF NOT public._is_dm_participant(p_conversation_id, v_caller) THEN
        RETURN jsonb_build_object('success', false, 'error', 'NOT_A_PARTICIPANT');
    END IF;

    IF v_conv.kind = 'direct' THEN
        v_peer_id := CASE WHEN v_conv.direct_user1_id = v_caller THEN v_conv.direct_user2_id ELSE v_conv.direct_user1_id END;
        IF public._are_blocked(v_caller, v_peer_id) THEN
            RETURN jsonb_build_object('success', false, 'error', 'BLOCKED');
        END IF;
    END IF;

    IF char_length(v_clean_content) > 4000 THEN
        RETURN jsonb_build_object('success', false, 'error', 'MESSAGE_TOO_LONG');
    END IF;

    IF v_clean_content = '' AND p_attachment_path IS NULL AND p_reply_to_note_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'EMPTY_MESSAGE');
    END IF;

    -- Idempotency check on client_temp_id for retries
    IF v_clean_temp_id IS NOT NULL THEN
        SELECT * INTO v_existing
        FROM public.dm_messages
        WHERE conversation_id = p_conversation_id
          AND sender_id = v_caller
          AND client_temp_id = v_clean_temp_id
        ORDER BY created_at DESC
        LIMIT 1;

        IF v_existing.id IS NOT NULL THEN
            SELECT username, handle, display_name, avatar_url, is_verified
            INTO v_sender_prof
            FROM public.profiles WHERE id = v_caller;

            IF v_existing.reply_to_message_id IS NOT NULL THEN
                SELECT jsonb_build_object(
                    'id', rep.id,
                    'sender_id', rep.sender_id,
                    'sender_display_name', rpr.display_name,
                    'content', CASE WHEN rep.deleted_at IS NOT NULL THEN 'Message deleted' ELSE rep.content END,
                    'attachment_mime', rep.attachment_mime,
                    'is_deleted', (rep.deleted_at IS NOT NULL)
                ) INTO v_reply_to_obj
                FROM public.dm_messages rep
                JOIN public.profiles rpr ON rpr.id = rep.sender_id
                WHERE rep.id = v_existing.reply_to_message_id
                  AND rep.conversation_id = p_conversation_id;
            END IF;

            RETURN jsonb_build_object(
                'success', true,
                'message', jsonb_build_object(
                    'id', v_existing.id,
                    'conversation_id', v_existing.conversation_id,
                    'sender_id', v_existing.sender_id,
                    'sender_username', v_sender_prof.username,
                    'sender_handle', v_sender_prof.handle,
                    'sender_display_name', v_sender_prof.display_name,
                    'sender_avatar_url', v_sender_prof.avatar_url,
                    'sender_is_verified', v_sender_prof.is_verified,
                    'message_type', v_existing.message_type,
                    'content', v_existing.content,
                    'reply_to', v_reply_to_obj,
                    'reply_to_note_id', v_existing.reply_to_note_id,
                    'note_snapshot_text', v_existing.note_snapshot_text,
                    'attachment_path', v_existing.attachment_path,
                    'attachment_mime', v_existing.attachment_mime,
                    'attachment_size', v_existing.attachment_size,
                    'client_temp_id', v_existing.client_temp_id,
                    'created_at', v_existing.created_at
                )
            );
        END IF;
    END IF;

    -- Prevent cross-conversation reply leakage
    IF v_reply_id IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM public.dm_messages
            WHERE id = v_reply_id AND conversation_id = p_conversation_id
        ) THEN
            v_reply_id := NULL;
        END IF;
    END IF;

    IF p_reply_to_note_id IS NOT NULL THEN
        v_msg_type := 'note_reply';
        SELECT substring(content from 1 for 60) INTO v_note_snapshot
        FROM public.notes WHERE id = p_reply_to_note_id;
    ELSIF p_attachment_path IS NOT NULL THEN
        v_msg_type := 'media';
    ELSE
        v_msg_type := 'text';
    END IF;

    INSERT INTO public.dm_messages (
        conversation_id,
        sender_id,
        message_type,
        content,
        reply_to_message_id,
        reply_to_note_id,
        note_snapshot_text,
        attachment_path,
        attachment_mime,
        attachment_size,
        client_temp_id
    ) VALUES (
        p_conversation_id,
        v_caller,
        v_msg_type,
        v_clean_content,
        v_reply_id,
        p_reply_to_note_id,
        v_note_snapshot,
        p_attachment_path,
        p_attachment_mime,
        p_attachment_size,
        v_clean_temp_id
    ) RETURNING id, created_at INTO v_msg_id, v_created_at;

    SELECT username, handle, display_name, avatar_url, is_verified
    INTO v_sender_prof
    FROM public.profiles WHERE id = v_caller;

    IF v_reply_id IS NOT NULL THEN
        SELECT jsonb_build_object(
            'id', rep.id,
            'sender_id', rep.sender_id,
            'sender_display_name', rpr.display_name,
            'content', CASE WHEN rep.deleted_at IS NOT NULL THEN 'Message deleted' ELSE rep.content END,
            'attachment_mime', rep.attachment_mime,
            'is_deleted', (rep.deleted_at IS NOT NULL)
        ) INTO v_reply_to_obj
        FROM public.dm_messages rep
        JOIN public.profiles rpr ON rpr.id = rep.sender_id
        WHERE rep.id = v_reply_id
          AND rep.conversation_id = p_conversation_id;
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'message', jsonb_build_object(
            'id', v_msg_id,
            'conversation_id', p_conversation_id,
            'sender_id', v_caller,
            'sender_username', v_sender_prof.username,
            'sender_handle', v_sender_prof.handle,
            'sender_display_name', v_sender_prof.display_name,
            'sender_avatar_url', v_sender_prof.avatar_url,
            'sender_is_verified', v_sender_prof.is_verified,
            'message_type', v_msg_type,
            'content', v_clean_content,
            'reply_to', v_reply_to_obj,
            'reply_to_note_id', p_reply_to_note_id,
            'note_snapshot_text', v_note_snapshot,
            'attachment_path', p_attachment_path,
            'attachment_mime', p_attachment_mime,
            'attachment_size', p_attachment_size,
            'client_temp_id', v_clean_temp_id,
            'created_at', v_created_at
        )
    );
END;
$$;

-- 6.9 toggle_pin_dm_message: allow DEFAULT NULL on p_message_id so unpin works when omitted
CREATE OR REPLACE FUNCTION public.toggle_pin_dm_message(
    p_conversation_id uuid,
    p_message_id uuid DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_current_pinned uuid;
    v_new_pinned uuid := NULL;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    IF NOT public._is_dm_participant(p_conversation_id, v_caller) THEN
        RETURN jsonb_build_object('success', false, 'error', 'NOT_A_PARTICIPANT');
    END IF;

    SELECT pinned_message_id INTO v_current_pinned
    FROM public.dm_conversations WHERE id = p_conversation_id;

    IF p_message_id IS NULL OR v_current_pinned = p_message_id THEN
        UPDATE public.dm_conversations SET pinned_message_id = NULL WHERE id = p_conversation_id;
        v_new_pinned := NULL;
    ELSE
        IF NOT EXISTS (
            SELECT 1 FROM public.dm_messages
            WHERE id = p_message_id AND conversation_id = p_conversation_id AND deleted_at IS NULL
        ) THEN
            RETURN jsonb_build_object('success', false, 'error', 'INVALID_MESSAGE');
        END IF;

        UPDATE public.dm_conversations SET pinned_message_id = p_message_id WHERE id = p_conversation_id;
        v_new_pinned := p_message_id;
    END IF;

    RETURN jsonb_build_object('success', true, 'pinned_message_id', v_new_pinned);
END;
$$;

-- 6.10 delete_dm_message: also unpin if deleted message was pinned
CREATE OR REPLACE FUNCTION public.delete_dm_message(p_message_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_msg record;
    v_deleted_at timestamptz := now();
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    SELECT * INTO v_msg FROM public.dm_messages WHERE id = p_message_id;
    IF v_msg.id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'MESSAGE_NOT_FOUND');
    END IF;

    IF v_msg.sender_id <> v_caller THEN
        RETURN jsonb_build_object('success', false, 'error', 'FORBIDDEN');
    END IF;

    UPDATE public.dm_messages
    SET deleted_at = v_deleted_at, content = ''
    WHERE id = p_message_id;

    UPDATE public.dm_conversations
    SET pinned_message_id = CASE WHEN pinned_message_id = p_message_id THEN NULL ELSE pinned_message_id END,
        last_message_text = CASE WHEN last_message_id = p_message_id THEN 'Message deleted' ELSE last_message_text END,
        updated_at = v_deleted_at
    WHERE id = v_msg.conversation_id
      AND (pinned_message_id = p_message_id OR last_message_id = p_message_id);

    RETURN jsonb_build_object('success', true, 'deleted_at', v_deleted_at);
END;
$$;

-- 6.11 set_conversation_pin: ignore left/hidden rows when counting max 3 pins
CREATE OR REPLACE FUNCTION public.set_conversation_pin(
    p_conversation_id uuid,
    p_pinned boolean
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_active_pins int;
    v_is_already_pinned boolean;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM public.dm_participants
        WHERE conversation_id = p_conversation_id AND user_id = v_user_id AND status NOT IN ('left', 'hidden')
    ) THEN
        RAISE EXCEPTION 'NOT_A_PARTICIPANT';
    END IF;

    IF p_pinned THEN
        SELECT (pinned_at IS NOT NULL) INTO v_is_already_pinned
        FROM public.dm_participants
        WHERE conversation_id = p_conversation_id AND user_id = v_user_id;

        IF NOT COALESCE(v_is_already_pinned, false) THEN
            PERFORM 1 FROM public.dm_participants
            WHERE user_id = v_user_id
            FOR UPDATE;

            SELECT count(*) INTO v_active_pins
            FROM public.dm_participants
            WHERE user_id = v_user_id
              AND pinned_at IS NOT NULL
              AND status NOT IN ('left', 'hidden');

            IF v_active_pins >= 3 THEN
                RAISE EXCEPTION 'PIN_LIMIT_REACHED';
            END IF;

            UPDATE public.dm_participants
            SET pinned_at = now(),
                is_pinned = true
            WHERE conversation_id = p_conversation_id AND user_id = v_user_id;
        END IF;
    ELSE
        UPDATE public.dm_participants
        SET pinned_at = NULL,
            is_pinned = false
        WHERE conversation_id = p_conversation_id AND user_id = v_user_id;
    END IF;

    RETURN jsonb_build_object('success', true, 'is_pinned', p_pinned);
END;
$$;

-- 6.12 delete_conversation_for_me: also clear marked_unread & advance last_read_at
CREATE OR REPLACE FUNCTION public.delete_conversation_for_me(
    p_conversation_id uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_user_id uuid := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    UPDATE public.dm_participants
    SET cleared_before = now(),
        last_read_at = now(),
        marked_unread = false,
        status = 'hidden',
        pinned_at = NULL,
        is_pinned = false
    WHERE conversation_id = p_conversation_id AND user_id = v_user_id;

    RETURN jsonb_build_object('success', true);
END;
$$;

-- 6.13 edit_user_message (AI chat): restore deleting subsequent messages atomically
CREATE OR REPLACE FUNCTION public.edit_user_message(p_message_id uuid, p_new_content text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_user uuid := auth.uid();
    v_clean text := trim(p_new_content);
    v_conv_id uuid;
    v_created_at timestamptz;
    v_deleted_count int := 0;
BEGIN
    IF v_user IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;
    IF char_length(v_clean) < 1 OR char_length(v_clean) > 8000 THEN
        RETURN jsonb_build_object('success', false, 'error', 'INVALID_LENGTH');
    END IF;

    SELECT m.conversation_id, m.created_at
    INTO v_conv_id, v_created_at
    FROM public.messages m
    JOIN public.conversations c ON c.id = m.conversation_id
    WHERE m.id = p_message_id
      AND c.user_id = v_user
      AND m.role = 'user';

    IF v_conv_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'NOT_FOUND');
    END IF;

    DELETE FROM public.messages
    WHERE conversation_id = v_conv_id
      AND created_at > v_created_at;

    GET DIAGNOSTICS v_deleted_count = ROW_COUNT;

    UPDATE public.messages
    SET content = v_clean,
        edited_at = now()
    WHERE id = p_message_id;

    RETURN jsonb_build_object(
        'success', true,
        'message_id', p_message_id,
        'conversation_id', v_conv_id,
        'deleted_messages_count', v_deleted_count
    );
END;
$$;

-- ---------------------------------------------------------------------------
-- 7. Strict Function ACL Hardening (Revoke PUBLIC & anon on all Messaging RPCs)
-- ---------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public.trg_conversations_sync_pin() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.trg_dm_messages_sync_last_message() FROM PUBLIC, anon, authenticated;

REVOKE ALL ON FUNCTION public._is_dm_participant(uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public._is_dm_participant(uuid, uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.get_or_create_direct_conversation(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_or_create_direct_conversation(uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.create_group_conversation(text, uuid[], text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_group_conversation(text, uuid[], text) TO authenticated;

REVOKE ALL ON FUNCTION public.add_group_members(uuid, uuid[]) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.add_group_members(uuid, uuid[]) TO authenticated;

REVOKE ALL ON FUNCTION public.remove_or_leave_group_member(uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.remove_or_leave_group_member(uuid, uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.update_group_info(uuid, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.update_group_info(uuid, text, text) TO authenticated;

REVOKE ALL ON FUNCTION public.get_dm_inbox(text, text, timestamptz, timestamptz, int) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_dm_inbox(text, text, timestamptz, timestamptz, int) TO authenticated;

REVOKE ALL ON FUNCTION public.get_dm_conversation_detail(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_dm_conversation_detail(uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.get_dm_messages(uuid, timestamptz, int) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_dm_messages(uuid, timestamptz, int) TO authenticated;

REVOKE ALL ON FUNCTION public.send_dm_message(uuid, text, uuid, uuid, text, text, bigint, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.send_dm_message(uuid, text, uuid, uuid, text, text, bigint, text) TO authenticated;

REVOKE ALL ON FUNCTION public.edit_dm_message(uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.edit_dm_message(uuid, text) TO authenticated;

REVOKE ALL ON FUNCTION public.delete_dm_message(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.delete_dm_message(uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.toggle_pin_dm_message(uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.toggle_pin_dm_message(uuid, uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.toggle_mute_dm_conversation(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.toggle_mute_dm_conversation(uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.toggle_pin_dm_conversation(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.toggle_pin_dm_conversation(uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.leave_or_delete_dm_conversation(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.leave_or_delete_dm_conversation(uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.mark_dm_unread(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mark_dm_unread(uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.set_conversation_pin(uuid, boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_conversation_pin(uuid, boolean) TO authenticated;

REVOKE ALL ON FUNCTION public.set_conversation_mute(uuid, timestamptz) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_conversation_mute(uuid, timestamptz) TO authenticated;

REVOKE ALL ON FUNCTION public.mark_conversation_unread(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mark_conversation_unread(uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.delete_conversation_for_me(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.delete_conversation_for_me(uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.leave_group(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.leave_group(uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.edit_user_message(uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.edit_user_message(uuid, text) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.register_push_token(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.register_push_token(text, text) TO authenticated;

COMMIT;

NOTIFY pgrst, 'reload schema';
NOTIFY pgrst, 'reload config';
