-- Migration: 20261112000005_dm_inbox_flawless_avatars_and_actions.sql
-- Description: Upgrades get_dm_inbox to return real member_avatars for groups and sender_name in last_message,
--              enhances mark_dm_read with optional last_read_message_id, and adds update_group_info.

CREATE OR REPLACE FUNCTION public.mark_dm_read(
    p_conversation_id uuid,
    p_last_read_message_id uuid DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_msg_id uuid := p_last_read_message_id;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    IF v_msg_id IS NULL THEN
        SELECT last_message_id INTO v_msg_id
        FROM public.dm_conversations
        WHERE id = p_conversation_id;
    END IF;

    UPDATE public.dm_participants
    SET
        last_read_at = now(),
        last_read_message_id = v_msg_id
    WHERE conversation_id = p_conversation_id AND user_id = v_caller;

    RETURN jsonb_build_object('success', true);
END;
$$;

REVOKE EXECUTE ON FUNCTION public.mark_dm_read(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.mark_dm_read(uuid, uuid) TO authenticated;

-- Upgrade get_dm_inbox to return member_avatars and sender_name
CREATE OR REPLACE FUNCTION public.get_dm_inbox(
    p_search text DEFAULT '',
    p_limit int DEFAULT 40,
    p_offset int DEFAULT 0
)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_clean_search text := trim(coalesce(p_search, ''));
    v_result jsonb;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    WITH caller_convs AS (
        SELECT
            c.id AS conversation_id,
            c.kind,
            c.title,
            c.avatar_url AS group_avatar_url,
            c.pinned_message_id,
            c.last_message_id,
            c.last_message_text,
            c.last_message_sender_id,
            c.last_message_at,
            c.created_at AS conv_created_at,
            p.status AS participant_status,
            p.is_muted,
            p.is_pinned,
            p.last_read_at,
            p.last_read_message_id,
            CASE
                WHEN c.kind = 'direct' THEN
                    CASE WHEN c.direct_user1_id = v_caller THEN c.direct_user2_id ELSE c.direct_user1_id END
                ELSE NULL
            END AS peer_user_id
        FROM public.dm_participants p
        JOIN public.dm_conversations c ON c.id = p.conversation_id
        WHERE p.user_id = v_caller
          AND p.status IN ('active', 'request')
    ),
    filtered_convs AS (
        SELECT
            cc.*,
            pr.username AS peer_username,
            pr.handle AS peer_handle,
            pr.display_name AS peer_display_name,
            pr.avatar_url AS peer_avatar_url,
            pr.is_verified AS peer_is_verified
        FROM caller_convs cc
        LEFT JOIN public.profiles pr ON pr.id = cc.peer_user_id
        WHERE (cc.peer_user_id IS NULL OR NOT public._are_blocked(v_caller, cc.peer_user_id))
          AND (
            v_clean_search = ''
            OR (cc.kind = 'group' AND cc.title ILIKE '%' || v_clean_search || '%')
            OR (cc.kind = 'direct' AND (pr.display_name ILIKE '%' || v_clean_search || '%' OR pr.handle ILIKE '%' || v_clean_search || '%'))
          )
    ),
    convs_with_stats AS (
        SELECT
            fc.*,
            COALESCE((
                SELECT count(*)::int
                FROM public.dm_messages m
                WHERE m.conversation_id = fc.conversation_id
                  AND m.created_at > fc.last_read_at
                  AND m.sender_id <> v_caller
                  AND m.deleted_at IS NULL
            ), 0) AS unread_count,
            (
                SELECT jsonb_build_object(
                    'id', lm.id,
                    'sender_id', lm.sender_id,
                    'sender_name', coalesce(sm_pr.display_name, sm_pr.username, 'User'),
                    'message_type', lm.message_type,
                    'content', lm.content,
                    'created_at', lm.created_at,
                    'deleted_at', lm.deleted_at
                )
                FROM public.dm_messages lm
                LEFT JOIN public.profiles sm_pr ON sm_pr.id = lm.sender_id
                WHERE lm.id = fc.last_message_id
            ) AS last_message_obj,
            CASE
                WHEN fc.kind = 'group' THEN (
                    SELECT count(*)::int
                    FROM public.dm_participants gp
                    WHERE gp.conversation_id = fc.conversation_id AND gp.status = 'active'
                )
                ELSE NULL
            END AS member_count,
            CASE
                WHEN fc.kind = 'group' THEN (
                    SELECT coalesce(jsonb_agg(
                        jsonb_build_object(
                            'user_id', gp.user_id,
                            'display_name', coalesce(mpr.display_name, mpr.username, 'User'),
                            'avatar_url', mpr.avatar_url
                        )
                    ), '[]'::jsonb)
                    FROM (
                        SELECT p2.user_id
                        FROM public.dm_participants p2
                        JOIN public.profiles pr2 ON pr2.id = p2.user_id
                        WHERE p2.conversation_id = fc.conversation_id
                          AND p2.status = 'active'
                        ORDER BY (pr2.avatar_url IS NOT NULL AND pr2.avatar_url <> '') DESC, p2.joined_at ASC
                        LIMIT 3
                    ) gp
                    JOIN public.profiles mpr ON mpr.id = gp.user_id
                )
                ELSE '[]'::jsonb
            END AS member_avatars
        FROM filtered_convs fc
        ORDER BY fc.is_pinned DESC, COALESCE(fc.last_message_at, fc.conv_created_at) DESC
        LIMIT GREATEST(1, LEAST(coalesce(p_limit, 40), 100))
        OFFSET GREATEST(0, coalesce(p_offset, 0))
    )
    SELECT coalesce(jsonb_agg(
        jsonb_build_object(
            'id', conversation_id,
            'kind', kind,
            'title', CASE WHEN kind = 'group' THEN title ELSE peer_display_name END,
            'avatar_url', CASE WHEN kind = 'group' THEN group_avatar_url ELSE peer_avatar_url END,
            'status', participant_status,
            'is_muted', is_muted,
            'is_pinned', is_pinned,
            'last_read_at', last_read_at,
            'last_read_message_id', last_read_message_id,
            'unread_count', unread_count,
            'last_message_text', last_message_text,
            'last_message_at', last_message_at,
            'last_message', last_message_obj,
            'peer', CASE
                WHEN kind = 'direct' THEN jsonb_build_object(
                    'id', peer_user_id,
                    'username', peer_username,
                    'handle', peer_handle,
                    'display_name', peer_display_name,
                    'avatar_url', peer_avatar_url,
                    'is_verified', peer_is_verified
                )
                ELSE NULL
            END,
            'member_count', member_count,
            'member_avatars', member_avatars
        )
    ), '[]'::jsonb) INTO v_result
    FROM convs_with_stats;

    RETURN v_result;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.get_dm_inbox(text, int, int) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_dm_inbox(text, int, int) TO authenticated;

-- Function to update group information (e.g. rename or avatar)
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
    v_clean_title text := trim(p_title);
    v_role text;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    SELECT role INTO v_role
    FROM public.dm_participants
    WHERE conversation_id = p_conversation_id AND user_id = v_caller AND status = 'active';

    IF v_role IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'NOT_A_MEMBER');
    END IF;

    IF v_clean_title = '' THEN
        RETURN jsonb_build_object('success', false, 'error', 'EMPTY_TITLE');
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

REVOKE EXECUTE ON FUNCTION public.update_group_info(uuid, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.update_group_info(uuid, text, text) TO authenticated;
