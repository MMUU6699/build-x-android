-- Migration: 20261112000008_dm_chat_info_and_inbox_normalization.sql
-- Description:
--   1. Re-assert the canonical get_dm_inbox contract with the exact filter
--      normalization requested by the client: only 'unread', 'groups' and
--      'requests' are meaningful; every other value (including '' and NULL)
--      resolves to 'all'. This makes the inbox resilient to a blank p_filter.
--   2. Add the peer's bio to get_dm_conversation_detail's peer_intro object so
--      the new Chat Info sheet can show a real bio snippet.
--   3. Keep EXECUTE restricted to authenticated callers (the function is a
--      SECURITY DEFINER app-owned RPC).
--
-- Rollback:
--   Recreate get_dm_inbox with the previous COALESCE(NULLIF(...)) normalization
--   and get_dm_conversation_detail without the 'bio' key. Both previous bodies
--   are in 20261112000006 / 20261112000007.

-- ---------------------------------------------------------------------------
-- 1. get_dm_inbox: normalize unknown filters to 'all'
-- ---------------------------------------------------------------------------
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
                WHEN c.kind != 'group' THEN (
                    SELECT p2.user_id
                    FROM public.dm_participants p2
                    WHERE p2.conversation_id = c.id AND p2.user_id != v_user_id
                    LIMIT 1
                )
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
            COALESCE(
                CASE
                    WHEN fc.last_message_sender_id = v_user_id THEN 'You'
                    ELSE split_part(sender_prof.display_name, ' ', 1)
                END,
                'User'
            ) AS sender_first_name,
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
                    SELECT jsonb_agg(
                        jsonb_build_object(
                            'user_id', gp.user_id,
                            'display_name', gprof.display_name,
                            'avatar_url', gprof.avatar_url
                        )
                    )
                    FROM (
                        SELECT gp.user_id
                        FROM public.dm_participants gp
                        JOIN public.profiles gprof ON gprof.id = gp.user_id
                        WHERE gp.conversation_id = fc.conversation_id AND gp.status = 'active'
                        ORDER BY (gprof.avatar_url IS NOT NULL) DESC, gp.joined_at ASC
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
            OR v_filter = 'requests'
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
            p_cursor_activity IS NULL
            OR (tf.is_pinned = false AND tf.last_activity_at < p_cursor_activity)
            OR (tf.is_pinned = true AND (p_cursor_pinned IS NULL OR tf.pinned_at < p_cursor_pinned))
        )
        ORDER BY tf.is_pinned DESC, tf.pinned_at DESC NULLS LAST, tf.last_activity_at DESC
        LIMIT LEAST(p_limit, 50)
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
                    'is_verified', COALESCE(p.peer_is_verified, false),
                    'is_pinned', p.is_pinned,
                    'pinned_at', p.pinned_at,
                    'is_muted', p.is_muted,
                    'muted_until', p.muted_until,
                    'is_marked_unread', p.marked_unread,
                    'unread_count', p.unread_count,
                    'last_message_id', p.last_message_id,
                    'last_message_text', p.last_message_text,
                    'last_message_type', p.last_message_type,
                    'last_message_sender_id', p.last_message_sender_id,
                    'sender_first_name', p.sender_first_name,
                    'last_message_at', p.last_message_at,
                    'last_activity_at', p.last_activity_at,
                    'member_count', p.member_count,
                    'member_avatars', p.member_avatars
                )
            ),
            '[]'::jsonb
        )
    ) INTO v_result
    FROM paginated p;

    RETURN v_result;
END;
$$;

REVOKE ALL ON FUNCTION public.get_dm_inbox(text, text, timestamptz, timestamptz, int) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.get_dm_inbox(text, text, timestamptz, timestamptz, int) TO authenticated;

-- ---------------------------------------------------------------------------
-- 2. get_dm_conversation_detail: expose peer bio for the Chat Info sheet
-- ---------------------------------------------------------------------------
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

    IF v_conv.pinned_message_id IS NOT NULL THEN
        SELECT jsonb_build_object(
            'id', pm.id,
            'sender_id', pm.sender_id,
            'sender_display_name', pr.display_name,
            'content', pm.content,
            'attachment_mime', pm.attachment_mime,
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
            'is_muted', p.is_muted,
            'is_pinned', p.is_pinned,
            'last_read_at', p.last_read_at,
            'last_read_message_id', p.last_read_message_id
        )
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
        'conversation', jsonb_build_object(
            'id', v_conv.id,
            'kind', v_conv.kind,
            'title', v_conv.title,
            'avatar_url', v_conv.avatar_url,
            'pinned_message_id', v_conv.pinned_message_id,
            'created_by', v_conv.created_by,
            'created_at', v_conv.created_at
        ),
        'pinned_message', v_pinned_obj,
        'participants', v_participants,
        'peer_intro', v_peer_intro
    );
END;
$$;

REVOKE ALL ON FUNCTION public.get_dm_conversation_detail(uuid) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.get_dm_conversation_detail(uuid) TO authenticated;
