-- DM Inbox Enhancements: leave_or_delete_dm_conversation and mark_dm_unread

CREATE OR REPLACE FUNCTION public.leave_or_delete_dm_conversation(p_conversation_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_conv record;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    SELECT * INTO v_conv FROM public.dm_conversations WHERE id = p_conversation_id;
    IF v_conv.id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'CONVERSATION_NOT_FOUND');
    END IF;

    -- Update caller's participant status to 'left'
    UPDATE public.dm_participants
    SET status = 'left',
        updated_at = now()
    WHERE conversation_id = p_conversation_id AND user_id = v_caller;

    -- If group chat, insert system message
    IF v_conv.kind = 'group' THEN
        INSERT INTO public.dm_messages (
            conversation_id, sender_id, message_type, content
        ) VALUES (
            p_conversation_id, v_caller, 'system', 'left the group'
        );
    END IF;

    RETURN jsonb_build_object('success', true);
END;
$$;

REVOKE EXECUTE ON FUNCTION public.leave_or_delete_dm_conversation(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.leave_or_delete_dm_conversation(uuid) TO authenticated;

CREATE OR REPLACE FUNCTION public.mark_dm_unread(p_conversation_id uuid)
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
    SET last_read_at = '1970-01-01 00:00:00+00',
        last_read_message_id = NULL
    WHERE conversation_id = p_conversation_id AND user_id = v_caller;

    RETURN jsonb_build_object('success', true);
END;
$$;

REVOKE EXECUTE ON FUNCTION public.mark_dm_unread(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.mark_dm_unread(uuid) TO authenticated;
