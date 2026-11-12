-- Migration: 20261112000007_fix_dm_inbox_and_constraints.sql
-- Description: Drop conflicting overloaded get_dm_inbox functions, update dm_participants_status_check to allow 'hidden',
-- and create the canonical unified get_dm_inbox function supporting filter, search, keyset pagination, block-filtering,
-- composite avatars, and safe defaults.

-- 1. Drop ALL older overloaded versions of get_dm_inbox
DROP FUNCTION IF EXISTS public.get_dm_inbox(text, int, int);
DROP FUNCTION IF EXISTS public.get_dm_inbox(text, timestamptz, timestamptz, int);
DROP FUNCTION IF EXISTS public.get_dm_inbox(text, text, timestamptz, timestamptz, int);

-- 2. Update status CHECK constraint on dm_participants to allow 'hidden'
ALTER TABLE public.dm_participants DROP CONSTRAINT IF EXISTS dm_participants_status_check;
ALTER TABLE public.dm_participants ADD CONSTRAINT dm_participants_status_check
  CHECK (status IN ('active', 'request', 'left', 'hidden'));

-- 3. Create canonical get_dm_inbox function
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
    v_filter text := COALESCE(NULLIF(lower(trim(p_filter)), ''), 'all');
    v_search text := trim(COALESCE(p_search, ''));
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
            -- Direct peer id
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
        -- Hide cleared conversation if no newer message arrived
        WHERE (cp.status != 'hidden' OR (c.last_message_at IS NOT NULL AND c.last_message_at > cp.cleared_before))
    ),
    filtered_convs AS (
        SELECT vc.*
        FROM valid_convs vc
        -- Exclude blocked users in direct chats
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
            -- Peer profile for 1:1
            prof.display_name AS peer_display_name,
            prof.handle AS peer_handle,
            prof.avatar_url AS peer_avatar_url,
            prof.is_verified AS peer_is_verified,
            -- Sender first name for preview prefix
            COALESCE(
                CASE 
                    WHEN fc.last_message_sender_id = v_user_id THEN 'You'
                    ELSE split_part(sender_prof.display_name, ' ', 1)
                END,
                'User'
            ) AS sender_first_name,
            -- Unread count calculation
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
            -- Group member count
            CASE 
                WHEN fc.kind = 'group' THEN (
                    SELECT count(*)::int 
                    FROM public.dm_participants gp 
                    WHERE gp.conversation_id = fc.conversation_id AND gp.status = 'active'
                )
                ELSE 2
            END AS member_count,
            -- Up to 4 member avatars for group chats
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

GRANT EXECUTE ON FUNCTION public.get_dm_inbox(text, text, timestamptz, timestamptz, int) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_dm_inbox(text, text, timestamptz, timestamptz, int) TO anon;
