-- Migration: 20261112000001_direct_and_group_messaging.sql
-- Direct & Group Messaging System (DMs, Group Chats, Notes Tray & Replies, Seen Receipts, Message Actions)

BEGIN;

-- 1. Create dm_conversations table
CREATE TABLE IF NOT EXISTS public.dm_conversations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    kind text NOT NULL CHECK (kind IN ('direct', 'group')),
    direct_user1_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE,
    direct_user2_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE,
    title text CHECK (title IS NULL OR (char_length(trim(title)) BETWEEN 1 AND 80)),
    avatar_url text,
    created_by uuid REFERENCES public.profiles(id) ON DELETE SET NULL,
    pinned_message_id uuid, -- FK added after dm_messages creation
    last_message_id uuid,
    last_message_text text,
    last_message_sender_id uuid REFERENCES public.profiles(id) ON DELETE SET NULL,
    last_message_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT dm_conversations_direct_pair_check CHECK (
        (kind = 'direct' AND direct_user1_id IS NOT NULL AND direct_user2_id IS NOT NULL AND direct_user1_id < direct_user2_id)
        OR
        (kind = 'group' AND direct_user1_id IS NULL AND direct_user2_id IS NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS dm_conversations_direct_unique_pair
    ON public.dm_conversations (direct_user1_id, direct_user2_id)
    WHERE kind = 'direct';

CREATE INDEX IF NOT EXISTS dm_conversations_last_message_at_idx
    ON public.dm_conversations (last_message_at DESC NULLS LAST);

-- 2. Create dm_participants table
CREATE TABLE IF NOT EXISTS public.dm_participants (
    conversation_id uuid NOT NULL REFERENCES public.dm_conversations(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    role text NOT NULL DEFAULT 'member' CHECK (role IN ('admin', 'member')),
    status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'request', 'left')),
    is_muted boolean NOT NULL DEFAULT false,
    is_pinned boolean NOT NULL DEFAULT false,
    last_read_at timestamptz NOT NULL DEFAULT now(),
    last_read_message_id uuid,
    joined_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, user_id)
);

CREATE INDEX IF NOT EXISTS dm_participants_user_idx
    ON public.dm_participants (user_id, status, is_pinned DESC);

CREATE INDEX IF NOT EXISTS dm_participants_read_receipts_idx
    ON public.dm_participants (conversation_id, last_read_message_id);

-- 3. Create dm_messages table
CREATE TABLE IF NOT EXISTS public.dm_messages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid NOT NULL REFERENCES public.dm_conversations(id) ON DELETE CASCADE,
    sender_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    message_type text NOT NULL DEFAULT 'text' CHECK (message_type IN ('text', 'media', 'note_reply', 'system')),
    content text NOT NULL DEFAULT '' CHECK (char_length(content) <= 4000),
    reply_to_message_id uuid REFERENCES public.dm_messages(id) ON DELETE SET NULL,
    reply_to_note_id uuid REFERENCES public.notes(id) ON DELETE SET NULL,
    note_snapshot_text text CHECK (note_snapshot_text IS NULL OR char_length(note_snapshot_text) <= 60),
    attachment_path text,
    attachment_mime text,
    attachment_size bigint,
    client_temp_id text,
    edited_at timestamptz,
    deleted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS dm_messages_conv_created_idx
    ON public.dm_messages (conversation_id, created_at DESC);

CREATE INDEX IF NOT EXISTS dm_messages_sender_idx
    ON public.dm_messages (sender_id);

CREATE INDEX IF NOT EXISTS dm_messages_client_temp_id_idx
    ON public.dm_messages (conversation_id, client_temp_id)
    WHERE client_temp_id IS NOT NULL;

-- Add foreign key constraint for pinned_message_id
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'dm_conversations_pinned_message_fk'
    ) THEN
        ALTER TABLE public.dm_conversations
            ADD CONSTRAINT dm_conversations_pinned_message_fk
            FOREIGN KEY (pinned_message_id) REFERENCES public.dm_messages(id) ON DELETE SET NULL;
    END IF;
END $$;

-- 4. Enhance reports table with message_id reference if not present
ALTER TABLE public.reports ADD COLUMN IF NOT EXISTS message_id uuid REFERENCES public.dm_messages(id) ON DELETE SET NULL;

-- 5. Helper function: _is_dm_participant
CREATE OR REPLACE FUNCTION public._is_dm_participant(p_conv_id uuid, p_user_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT p_user_id IS NOT NULL AND EXISTS (
        SELECT 1 FROM public.dm_participants
        WHERE conversation_id = p_conv_id
          AND user_id = p_user_id
          AND status IN ('active', 'request')
    );
$$;

REVOKE EXECUTE ON FUNCTION public._is_dm_participant(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public._is_dm_participant(uuid, uuid) TO authenticated;

-- 6. Enable RLS and define SELECT policies
ALTER TABLE public.dm_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.dm_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.dm_messages ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS dm_conversations_select ON public.dm_conversations;
CREATE POLICY dm_conversations_select ON public.dm_conversations
    FOR SELECT TO authenticated
    USING (public._is_dm_participant(id, auth.uid()));

DROP POLICY IF EXISTS dm_participants_select ON public.dm_participants;
CREATE POLICY dm_participants_select ON public.dm_participants
    FOR SELECT TO authenticated
    USING (public._is_dm_participant(conversation_id, auth.uid()));

DROP POLICY IF EXISTS dm_messages_select ON public.dm_messages;
CREATE POLICY dm_messages_select ON public.dm_messages
    FOR SELECT TO authenticated
    USING (public._is_dm_participant(conversation_id, auth.uid()));

-- 7. Add tables to supabase_realtime publication
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime' AND tablename = 'dm_conversations'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.dm_conversations;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime' AND tablename = 'dm_participants'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.dm_participants;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime' AND tablename = 'dm_messages'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.dm_messages;
    END IF;
END $$;

-- 8. Hardened RPCs

-- 8.1 get_or_create_direct_conversation
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
    v_is_new boolean := false;
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
        -- Ensure caller participant is active
        INSERT INTO public.dm_participants (conversation_id, user_id, role, status)
        VALUES (v_conv_id, v_caller, 'member', 'active')
        ON CONFLICT (conversation_id, user_id)
        DO UPDATE SET status = CASE WHEN dm_participants.status = 'left' THEN 'active' ELSE dm_participants.status END;

        RETURN jsonb_build_object(
            'success', true,
            'conversation_id', v_conv_id,
            'is_new', false
        );
    END IF;

    -- If target follows caller, status is active; otherwise message request
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

    INSERT INTO public.dm_participants (conversation_id, user_id, role, status)
    VALUES
        (v_conv_id, v_caller, 'member', 'active'),
        (v_conv_id, target_user_id, 'member', v_target_status);

    RETURN jsonb_build_object(
        'success', true,
        'conversation_id', v_conv_id,
        'is_new', true
    );
END;
$$;

-- 8.2 create_group_conversation
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

    -- Add creator as admin
    INSERT INTO public.dm_participants (conversation_id, user_id, role, status)
    VALUES (v_conv_id, v_caller, 'admin', 'active');

    -- Add members
    IF p_member_ids IS NOT NULL THEN
        FOREACH v_member_id IN ARRAY p_member_ids LOOP
            IF v_member_id IS NOT NULL AND v_member_id <> v_caller THEN
                IF EXISTS (SELECT 1 FROM public.profiles WHERE id = v_member_id) AND NOT public._are_blocked(v_caller, v_member_id) THEN
                    INSERT INTO public.dm_participants (conversation_id, user_id, role, status)
                    VALUES (v_conv_id, v_member_id, 'member', 'active')
                    ON CONFLICT DO NOTHING;
                    v_added_count := v_added_count + 1;
                END IF;
            END IF;
        END LOOP;
    END IF;

    -- System message for group creation
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

-- 8.3 add_group_members
CREATE OR REPLACE FUNCTION public.add_group_members(
    p_conversation_id uuid,
    p_member_ids uuid[]
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_kind text;
    v_member_id uuid;
    v_added_count int := 0;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    SELECT kind INTO v_kind FROM public.dm_conversations WHERE id = p_conversation_id;
    IF v_kind IS NULL OR v_kind <> 'group' THEN
        RETURN jsonb_build_object('success', false, 'error', 'NOT_A_GROUP');
    END IF;

    IF NOT public._is_dm_participant(p_conversation_id, v_caller) THEN
        RETURN jsonb_build_object('success', false, 'error', 'NOT_A_PARTICIPANT');
    END IF;

    IF p_member_ids IS NOT NULL THEN
        FOREACH v_member_id IN ARRAY p_member_ids LOOP
            IF v_member_id IS NOT NULL AND v_member_id <> v_caller THEN
                IF EXISTS (SELECT 1 FROM public.profiles WHERE id = v_member_id) AND NOT public._are_blocked(v_caller, v_member_id) THEN
                    INSERT INTO public.dm_participants (conversation_id, user_id, role, status)
                    VALUES (p_conversation_id, v_member_id, 'member', 'active')
                    ON CONFLICT (conversation_id, user_id)
                    DO UPDATE SET status = 'active' WHERE dm_participants.status = 'left';
                    v_added_count := v_added_count + 1;
                END IF;
            END IF;
        END LOOP;
    END IF;

    RETURN jsonb_build_object('success', true, 'added_count', v_added_count);
END;
$$;

-- 8.4 remove_or_leave_group_member
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
        -- Leaving
        UPDATE public.dm_participants SET status = 'left'
        WHERE conversation_id = p_conversation_id AND user_id = v_caller;
        RETURN jsonb_build_object('success', true, 'action', 'left');
    ELSE
        -- Removing another user requires admin role
        IF v_caller_role <> 'admin' THEN
            RETURN jsonb_build_object('success', false, 'error', 'NOT_ADMIN');
        END IF;

        UPDATE public.dm_participants SET status = 'left'
        WHERE conversation_id = p_conversation_id AND user_id = p_target_user_id;
        RETURN jsonb_build_object('success', true, 'action', 'removed');
    END IF;
END;
$$;

-- 8.5 get_dm_inbox
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
                    'message_type', lm.message_type,
                    'content', lm.content,
                    'created_at', lm.created_at,
                    'deleted_at', lm.deleted_at
                )
                FROM public.dm_messages lm
                WHERE lm.id = fc.last_message_id
            ) AS last_message_obj,
            CASE
                WHEN fc.kind = 'group' THEN (
                    SELECT count(*)::int
                    FROM public.dm_participants gp
                    WHERE gp.conversation_id = fc.conversation_id AND gp.status = 'active'
                )
                ELSE NULL
            END AS member_count
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
            'member_count', member_count
        )
    ), '[]'::jsonb) INTO v_result
    FROM convs_with_stats;

    RETURN v_result;
END;
$$;

-- 8.6 get_followed_active_notes
CREATE OR REPLACE FUNCTION public.get_followed_active_notes()
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_result jsonb;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    WITH visible_notes AS (
        SELECT
            n.id,
            n.user_id,
            n.content,
            n.created_at,
            n.expires_at,
            pr.username,
            pr.handle,
            pr.display_name,
            pr.avatar_url,
            pr.is_verified,
            CASE WHEN n.user_id = v_caller THEN 0 ELSE 1 END AS sort_priority
        FROM public.notes n
        JOIN public.profiles pr ON pr.id = n.user_id
        WHERE n.is_active = true
          AND n.expires_at > now()
          AND (
            n.user_id = v_caller
            OR n.user_id IN (
                SELECT following_id FROM public.follows WHERE follower_id = v_caller
            )
          )
          AND NOT public._are_blocked(v_caller, n.user_id)
        ORDER BY sort_priority ASC, n.created_at DESC
        LIMIT 30
    )
    SELECT coalesce(jsonb_agg(
        jsonb_build_object(
            'id', id,
            'user_id', user_id,
            'content', content,
            'created_at', created_at,
            'expires_at', expires_at,
            'is_own', (user_id = v_caller),
            'author', jsonb_build_object(
                'id', user_id,
                'username', username,
                'handle', handle,
                'display_name', display_name,
                'avatar_url', avatar_url,
                'is_verified', is_verified
            )
        )
    ), '[]'::jsonb) INTO v_result
    FROM visible_notes;

    RETURN v_result;
END;
$$;

-- 8.7 get_dm_conversation_detail
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

    -- Pinned message object
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

    -- Participants list
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

    -- If direct, build peer intro card data
    IF v_conv.kind = 'direct' THEN
        v_peer_id := CASE WHEN v_conv.direct_user1_id = v_caller THEN v_conv.direct_user2_id ELSE v_conv.direct_user1_id END;

        SELECT jsonb_build_object(
            'id', pr.id,
            'username', pr.username,
            'handle', pr.handle,
            'display_name', pr.display_name,
            'avatar_url', pr.avatar_url,
            'is_verified', pr.is_verified,
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

-- 8.8 get_dm_messages
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
    v_result jsonb;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    IF NOT public._is_dm_participant(p_conversation_id, v_caller) THEN
        RETURN jsonb_build_object('success', false, 'error', 'NOT_A_PARTICIPANT');
    END IF;

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
                )
                ELSE NULL
            END AS reply_to_obj
        FROM raw_messages rm
        ORDER BY rm.created_at ASC
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
    ), '[]'::jsonb) INTO v_result
    FROM messages_with_reply;

    RETURN v_result;
END;
$$;

-- 8.9 send_dm_message
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
    v_note_snapshot text := NULL;
    v_msg_id uuid;
    v_created_at timestamptz;
    v_reply_to_obj jsonb := NULL;
    v_sender_prof record;
    v_summary_text text;
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

    -- Block check for direct chats
    IF v_conv.kind = 'direct' THEN
        v_peer_id := CASE WHEN v_conv.direct_user1_id = v_caller THEN v_conv.direct_user2_id ELSE v_conv.direct_user1_id END;
        IF public._are_blocked(v_caller, v_peer_id) THEN
            RETURN jsonb_build_object('success', false, 'error', 'BLOCKED');
        END IF;
    END IF;

    -- Determine message type
    IF p_reply_to_note_id IS NOT NULL THEN
        v_msg_type := 'note_reply';
        SELECT substring(content from 1 for 60) INTO v_note_snapshot
        FROM public.notes WHERE id = p_reply_to_note_id;
    ELSIF p_attachment_path IS NOT NULL THEN
        v_msg_type := 'media';
    ELSE
        v_msg_type := 'text';
    END IF;

    -- Insert message
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
        coalesce(trim(p_content), ''),
        p_reply_to_message_id,
        p_reply_to_note_id,
        v_note_snapshot,
        p_attachment_path,
        p_attachment_mime,
        p_attachment_size,
        p_client_temp_id
    ) RETURNING id, created_at INTO v_msg_id, v_created_at;

    -- Update last message on conversation
    v_summary_text := CASE
        WHEN v_msg_type = 'media' THEN '[Media]'
        WHEN v_msg_type = 'note_reply' THEN coalesce(trim(p_content), '[Note reply]')
        ELSE trim(p_content)
    END;

    UPDATE public.dm_conversations
    SET
        last_message_id = v_msg_id,
        last_message_text = v_summary_text,
        last_message_sender_id = v_caller,
        last_message_at = v_created_at,
        updated_at = v_created_at
    WHERE id = p_conversation_id;

    -- Update sender's participant status to active and read receipt
    UPDATE public.dm_participants
    SET
        status = 'active',
        last_read_at = v_created_at,
        last_read_message_id = v_msg_id
    WHERE conversation_id = p_conversation_id AND user_id = v_caller;

    -- Get sender profile details
    SELECT username, handle, display_name, avatar_url, is_verified
    INTO v_sender_prof
    FROM public.profiles WHERE id = v_caller;

    -- Get reply preview if present
    IF p_reply_to_message_id IS NOT NULL THEN
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
        WHERE rep.id = p_reply_to_message_id;
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
            'content', coalesce(trim(p_content), ''),
            'reply_to', v_reply_to_obj,
            'reply_to_note_id', p_reply_to_note_id,
            'note_snapshot_text', v_note_snapshot,
            'attachment_path', p_attachment_path,
            'attachment_mime', p_attachment_mime,
            'attachment_size', p_attachment_size,
            'client_temp_id', p_client_temp_id,
            'created_at', v_created_at
        )
    );
END;
$$;

-- 8.10 mark_dm_read
CREATE OR REPLACE FUNCTION public.mark_dm_read(
    p_conversation_id uuid,
    p_last_read_message_id uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    UPDATE public.dm_participants
    SET
        last_read_at = now(),
        last_read_message_id = p_last_read_message_id
    WHERE conversation_id = p_conversation_id AND user_id = v_caller;

    RETURN jsonb_build_object('success', true);
END;
$$;

-- 8.11 edit_dm_message
CREATE OR REPLACE FUNCTION public.edit_dm_message(
    p_message_id uuid,
    p_new_content text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_msg record;
    v_clean_content text := trim(p_new_content);
    v_edited_at timestamptz := now();
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

    IF v_msg.deleted_at IS NOT NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'ALREADY_DELETED');
    END IF;

    IF v_msg.message_type = 'system' THEN
        RETURN jsonb_build_object('success', false, 'error', 'CANNOT_EDIT_SYSTEM');
    END IF;

    IF v_clean_content IS NULL OR char_length(v_clean_content) < 1 OR char_length(v_clean_content) > 4000 THEN
        RETURN jsonb_build_object('success', false, 'error', 'INVALID_CONTENT');
    END IF;

    UPDATE public.dm_messages
    SET content = v_clean_content, edited_at = v_edited_at
    WHERE id = p_message_id;

    -- Update last_message_text on conversation if this was the latest message
    UPDATE public.dm_conversations
    SET last_message_text = v_clean_content, updated_at = v_edited_at
    WHERE id = v_msg.conversation_id AND last_message_id = p_message_id;

    RETURN jsonb_build_object('success', true, 'edited_at', v_edited_at);
END;
$$;

-- 8.12 delete_dm_message
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
    SET last_message_text = 'Message deleted', updated_at = v_deleted_at
    WHERE id = v_msg.conversation_id AND last_message_id = p_message_id;

    RETURN jsonb_build_object('success', true, 'deleted_at', v_deleted_at);
END;
$$;

-- 8.13 toggle_pin_dm_message
CREATE OR REPLACE FUNCTION public.toggle_pin_dm_message(
    p_conversation_id uuid,
    p_message_id uuid
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
        -- Unpin
        UPDATE public.dm_conversations SET pinned_message_id = NULL WHERE id = p_conversation_id;
        v_new_pinned := NULL;
    ELSE
        -- Pin
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

-- 8.14 toggle_mute_dm_conversation
CREATE OR REPLACE FUNCTION public.toggle_mute_dm_conversation(p_conversation_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_muted boolean;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    UPDATE public.dm_participants
    SET is_muted = NOT is_muted
    WHERE conversation_id = p_conversation_id AND user_id = v_caller
    RETURNING is_muted INTO v_muted;

    IF v_muted IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'NOT_A_PARTICIPANT');
    END IF;

    RETURN jsonb_build_object('success', true, 'is_muted', v_muted);
END;
$$;

-- 8.15 toggle_pin_dm_conversation
CREATE OR REPLACE FUNCTION public.toggle_pin_dm_conversation(p_conversation_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_pinned boolean;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    UPDATE public.dm_participants
    SET is_pinned = NOT is_pinned
    WHERE conversation_id = p_conversation_id AND user_id = v_caller
    RETURNING is_pinned INTO v_pinned;

    IF v_pinned IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'NOT_A_PARTICIPANT');
    END IF;

    RETURN jsonb_build_object('success', true, 'is_pinned', v_pinned);
END;
$$;

-- 9. Explicit Grants
REVOKE EXECUTE ON FUNCTION public.get_or_create_direct_conversation(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_or_create_direct_conversation(uuid) TO authenticated;

REVOKE EXECUTE ON FUNCTION public.create_group_conversation(text, uuid[], text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.create_group_conversation(text, uuid[], text) TO authenticated;

REVOKE EXECUTE ON FUNCTION public.add_group_members(uuid, uuid[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.add_group_members(uuid, uuid[]) TO authenticated;

REVOKE EXECUTE ON FUNCTION public.remove_or_leave_group_member(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.remove_or_leave_group_member(uuid, uuid) TO authenticated;

REVOKE EXECUTE ON FUNCTION public.get_dm_inbox(text, int, int) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_dm_inbox(text, int, int) TO authenticated;

REVOKE EXECUTE ON FUNCTION public.get_followed_active_notes() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_followed_active_notes() TO authenticated;

REVOKE EXECUTE ON FUNCTION public.get_dm_conversation_detail(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_dm_conversation_detail(uuid) TO authenticated;

REVOKE EXECUTE ON FUNCTION public.get_dm_messages(uuid, timestamptz, int) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_dm_messages(uuid, timestamptz, int) TO authenticated;

REVOKE EXECUTE ON FUNCTION public.send_dm_message(uuid, text, uuid, uuid, text, text, bigint, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.send_dm_message(uuid, text, uuid, uuid, text, text, bigint, text) TO authenticated;

REVOKE EXECUTE ON FUNCTION public.mark_dm_read(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.mark_dm_read(uuid, uuid) TO authenticated;

REVOKE EXECUTE ON FUNCTION public.edit_dm_message(uuid, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.edit_dm_message(uuid, text) TO authenticated;

REVOKE EXECUTE ON FUNCTION public.delete_dm_message(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.delete_dm_message(uuid) TO authenticated;

REVOKE EXECUTE ON FUNCTION public.toggle_pin_dm_message(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.toggle_pin_dm_message(uuid, uuid) TO authenticated;

REVOKE EXECUTE ON FUNCTION public.toggle_mute_dm_conversation(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.toggle_mute_dm_conversation(uuid) TO authenticated;

REVOKE EXECUTE ON FUNCTION public.toggle_pin_dm_conversation(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.toggle_pin_dm_conversation(uuid) TO authenticated;

COMMIT;
