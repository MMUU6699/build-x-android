-- Migration: 20261112000006_dm_inbox_modern_restructure.sql
-- Description: Modern Direct & Group Messaging Inbox Schema, Keyset Pagination, Hardened RPCs, Concurrency-Safe Pinning (max 3), Muting, Clear/Delete, and Leave Group.

-- 1. Additive columns on dm_participants
ALTER TABLE public.dm_participants
  ADD COLUMN IF NOT EXISTS pinned_at timestamptz NULL,
  ADD COLUMN IF NOT EXISTS muted_until timestamptz NULL,
  ADD COLUMN IF NOT EXISTS marked_unread boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS cleared_before timestamptz NULL,
  ADD COLUMN IF NOT EXISTS left_at timestamptz NULL,
  ADD COLUMN IF NOT EXISTS last_activity_at timestamptz NOT NULL DEFAULT now();

-- 2. Additive columns on dm_conversations
ALTER TABLE public.dm_conversations
  ADD COLUMN IF NOT EXISTS last_message_type text NULL DEFAULT 'text';

-- 3. Data migration (convert, do not lose: grandfather existing pins and mutes)
UPDATE public.dm_participants 
SET pinned_at = now() 
WHERE is_pinned = true AND pinned_at IS NULL;

UPDATE public.dm_participants 
SET muted_until = 'infinity'::timestamptz 
WHERE is_muted = true AND muted_until IS NULL;

UPDATE public.dm_participants p
SET last_activity_at = COALESCE(c.last_message_at, p.joined_at, now())
FROM public.dm_conversations c
WHERE c.id = p.conversation_id;

-- 4. High-performance composite indexes
CREATE INDEX IF NOT EXISTS idx_dm_part_inbox_keyset
ON public.dm_participants (user_id, (pinned_at IS NOT NULL) DESC, pinned_at DESC NULLS LAST, last_activity_at DESC);

CREATE INDEX IF NOT EXISTS idx_dm_msg_conv_created
ON public.dm_messages (conversation_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_dm_blocks_lookup
ON public.blocks (blocker_id, blocked_id);

-- 5. Trigger to denormalize and maintain conversation & participant last_activity
CREATE OR REPLACE FUNCTION public.trg_dm_messages_sync_last_message()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_sender_name text;
  v_preview_text text;
  v_prev_id uuid;
  v_prev_content text;
  v_prev_type text;
  v_prev_sender uuid;
  v_prev_created timestamptz;
BEGIN
  IF TG_OP = 'INSERT' THEN
    -- Get sender name
    SELECT COALESCE(display_name, 'User') INTO v_sender_name
    FROM public.profiles WHERE id = NEW.sender_id;

    v_preview_text := CASE 
      WHEN NEW.deleted_at IS NOT NULL THEN 'Message deleted'
      WHEN NEW.message_type = 'image' THEN 'Photo'
      ELSE NEW.content
    END;

    -- Update shared conversation
    UPDATE public.dm_conversations
    SET last_message_id = NEW.id,
        last_message_text = v_preview_text,
        last_message_sender_id = NEW.sender_id,
        last_message_type = NEW.message_type,
        last_message_at = NEW.created_at,
        updated_at = now()
    WHERE id = NEW.conversation_id;

    -- Update participant last_activity_at and unhide conversation for participants who cleared it earlier
    UPDATE public.dm_participants
    SET last_activity_at = NEW.created_at,
        status = CASE WHEN status = 'hidden' THEN 'active' ELSE status END
    WHERE conversation_id = NEW.conversation_id AND status != 'left';

    -- Auto-mark read and un-mark unread for the sender
    UPDATE public.dm_participants
    SET last_read_at = NEW.created_at,
        last_read_message_id = NEW.id,
        marked_unread = false
    WHERE conversation_id = NEW.conversation_id AND user_id = NEW.sender_id;

    RETURN NEW;

  ELSIF TG_OP = 'UPDATE' THEN
    -- If the updated message is the last message of the conversation, sync preview
    IF NEW.id = (SELECT last_message_id FROM public.dm_conversations WHERE id = NEW.conversation_id) THEN
      v_preview_text := CASE 
        WHEN NEW.deleted_at IS NOT NULL THEN 'Message deleted'
        WHEN NEW.message_type = 'image' THEN 'Photo'
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
    -- If the deleted message is the last message, find the preceding message
    IF OLD.id = (SELECT last_message_id FROM public.dm_conversations WHERE id = OLD.conversation_id) THEN
      SELECT id, 
             CASE WHEN deleted_at IS NOT NULL THEN 'Message deleted' WHEN message_type = 'image' THEN 'Photo' ELSE content END,
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

DROP TRIGGER IF EXISTS trg_dm_messages_last_sync ON public.dm_messages;
CREATE TRIGGER trg_dm_messages_last_sync
AFTER INSERT OR UPDATE OR DELETE ON public.dm_messages
FOR EACH ROW
EXECUTE FUNCTION public.trg_dm_messages_sync_last_message();

-- 6. RPC: set_conversation_pin (concurrency-safe with row lock, max 3 limit for NEW pins)
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
        WHERE conversation_id = p_conversation_id AND user_id = v_user_id AND status != 'left'
    ) THEN
        RAISE EXCEPTION 'NOT_A_PARTICIPANT';
    END IF;

    IF p_pinned THEN
        -- Check if already pinned
        SELECT (pinned_at IS NOT NULL) INTO v_is_already_pinned
        FROM public.dm_participants
        WHERE conversation_id = p_conversation_id AND user_id = v_user_id;

        IF NOT COALESCE(v_is_already_pinned, false) THEN
            -- Lock participant rows for this user to prevent concurrent race condition exceeding 3 pins
            PERFORM 1 FROM public.dm_participants
            WHERE user_id = v_user_id
            FOR UPDATE;

            SELECT count(*) INTO v_active_pins
            FROM public.dm_participants
            WHERE user_id = v_user_id AND pinned_at IS NOT NULL;

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

-- 7. RPC: set_conversation_mute (time-bounded or until turned off)
CREATE OR REPLACE FUNCTION public.set_conversation_mute(
    p_conversation_id uuid,
    p_until timestamptz DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_is_muted boolean;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM public.dm_participants
        WHERE conversation_id = p_conversation_id AND user_id = v_user_id AND status != 'left'
    ) THEN
        RAISE EXCEPTION 'NOT_A_PARTICIPANT';
    END IF;

    v_is_muted := (p_until IS NOT NULL);

    UPDATE public.dm_participants
    SET muted_until = p_until,
        is_muted = v_is_muted
    WHERE conversation_id = p_conversation_id AND user_id = v_user_id;

    RETURN jsonb_build_object('success', true, 'is_muted', v_is_muted, 'muted_until', p_until);
END;
$$;

-- 8. RPC: mark_conversation_unread & mark_conversation_read
CREATE OR REPLACE FUNCTION public.mark_conversation_unread(
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
    SET marked_unread = true
    WHERE conversation_id = p_conversation_id AND user_id = v_user_id AND status != 'left';

    RETURN jsonb_build_object('success', true, 'is_marked_unread', true);
END;
$$;

CREATE OR REPLACE FUNCTION public.mark_conversation_read(
    p_conversation_id uuid,
    p_last_read_message_id uuid DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_msg_id uuid := p_last_read_message_id;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    IF v_msg_id IS NULL THEN
        SELECT last_message_id INTO v_msg_id
        FROM public.dm_conversations
        WHERE id = p_conversation_id;
    END IF;

    UPDATE public.dm_participants
    SET marked_unread = false,
        last_read_at = now(),
        last_read_message_id = COALESCE(v_msg_id, last_read_message_id)
    WHERE conversation_id = p_conversation_id AND user_id = v_user_id AND status != 'left';

    RETURN jsonb_build_object('success', true, 'is_marked_unread', false);
END;
$$;

-- 9. RPC: delete_conversation_for_me (cleared_before + hides until new message)
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
        status = 'hidden',
        pinned_at = NULL,
        is_pinned = false
    WHERE conversation_id = p_conversation_id AND user_id = v_user_id;

    RETURN jsonb_build_object('success', true);
END;
$$;

-- 10. RPC: leave_group (admin transfer or delete group if last member)
CREATE OR REPLACE FUNCTION public.leave_group(
    p_conversation_id uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_conv_kind text;
    v_caller_role text;
    v_active_count int;
    v_other_admin_count int;
    v_new_admin_id uuid;
    v_user_display_name text;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    SELECT kind INTO v_conv_kind
    FROM public.dm_conversations
    WHERE id = p_conversation_id;

    IF v_conv_kind != 'group' THEN
        RAISE EXCEPTION 'NOT_A_GROUP_CONVERSATION';
    END IF;

    SELECT role INTO v_caller_role
    FROM public.dm_participants
    WHERE conversation_id = p_conversation_id AND user_id = v_user_id AND status = 'active';

    IF v_caller_role IS NULL THEN
        RAISE EXCEPTION 'NOT_AN_ACTIVE_MEMBER';
    END IF;

    SELECT count(*) INTO v_active_count
    FROM public.dm_participants
    WHERE conversation_id = p_conversation_id AND status = 'active';

    IF v_active_count <= 1 THEN
        -- Last member: delete the conversation completely
        DELETE FROM public.dm_conversations WHERE id = p_conversation_id;
        RETURN jsonb_build_object('success', true, 'deleted', true);
    ELSE
        -- Check admin count
        IF v_caller_role = 'admin' THEN
            SELECT count(*) INTO v_other_admin_count
            FROM public.dm_participants
            WHERE conversation_id = p_conversation_id AND user_id != v_user_id AND role = 'admin' AND status = 'active';

            IF v_other_admin_count = 0 THEN
                -- Promote longest-standing active member
                SELECT user_id INTO v_new_admin_id
                FROM public.dm_participants
                WHERE conversation_id = p_conversation_id AND user_id != v_user_id AND status = 'active'
                ORDER BY joined_at ASC
                LIMIT 1;

                IF v_new_admin_id IS NOT NULL THEN
                    UPDATE public.dm_participants
                    SET role = 'admin'
                    WHERE conversation_id = p_conversation_id AND user_id = v_new_admin_id;
                END IF;
            END IF;
        END IF;

        -- Get user name for system message
        SELECT COALESCE(display_name, 'Member') INTO v_user_display_name
        FROM public.profiles
        WHERE id = v_user_id;

        -- Post system message
        INSERT INTO public.dm_messages (
            conversation_id,
            sender_id,
            message_type,
            content
        ) VALUES (
            p_conversation_id,
            v_user_id,
            'system',
            v_user_display_name || ' left'
        );

        -- Mark participant as left
        UPDATE public.dm_participants
        SET status = 'left',
            left_at = now(),
            pinned_at = NULL,
            is_pinned = false
        WHERE conversation_id = p_conversation_id AND user_id = v_user_id;

        RETURN jsonb_build_object('success', true, 'left', true, 'new_admin_id', v_new_admin_id);
    END IF;
END;
$$;

-- 11. RPC: get_dm_inbox (single-query keyset pagination, block-aware, cleared-aware, up to 4 member avatars)
CREATE OR REPLACE FUNCTION public.get_dm_inbox(
    p_filter text DEFAULT 'all',
    p_cursor_pinned timestamptz DEFAULT NULL,
    p_cursor_activity timestamptz DEFAULT NULL,
    p_limit int DEFAULT 30
)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_user_id uuid := auth.uid();
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
            p_filter = 'all'
            OR (p_filter = 'unread' AND ec.unread_count > 0)
            OR (p_filter = 'groups' AND ec.kind = 'group')
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

-- Grant EXECUTE to authenticated users
GRANT EXECUTE ON FUNCTION public.set_conversation_pin(uuid, boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.set_conversation_mute(uuid, timestamptz) TO authenticated;
GRANT EXECUTE ON FUNCTION public.mark_conversation_unread(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.mark_conversation_read(uuid, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.delete_conversation_for_me(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.leave_group(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_dm_inbox(text, timestamptz, timestamptz, int) TO authenticated;
