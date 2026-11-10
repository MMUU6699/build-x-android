-- 20261110000002_message_actions.sql
-- Additive only: nullable column and edit_user_message RPC
-- Preserves backup schema, zero mock data, strict ownership verification

-- 1. Add nullable edited_at column to messages
ALTER TABLE public.messages
  ADD COLUMN IF NOT EXISTS edited_at timestamptz DEFAULT NULL;

-- 2. Create edit_user_message RPC
CREATE OR REPLACE FUNCTION public.edit_user_message(
    p_message_id uuid,
    p_new_content text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_conv_id uuid;
    v_created_at timestamptz;
    v_user_id uuid;
    v_deleted_count int;
BEGIN
    -- Verify message exists and is a user message
    SELECT conversation_id, created_at, user_id
    INTO v_conv_id, v_created_at, v_user_id
    FROM public.messages
    WHERE id = p_message_id AND role = 'user';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'User message not found' USING ERRCODE = 'P0002';
    END IF;

    -- Verify ownership: caller must be author or conversation owner
    IF v_user_id <> auth.uid() THEN
        IF NOT EXISTS (
            SELECT 1 FROM public.conversations
            WHERE id = v_conv_id AND user_id = auth.uid()
        ) THEN
            RAISE EXCEPTION 'Access denied: not conversation owner' USING ERRCODE = '42501';
        END IF;
    END IF;

    -- Delete later messages in this conversation (cascade deletes attachment rows; storage files preserved)
    DELETE FROM public.messages
    WHERE conversation_id = v_conv_id
      AND created_at > v_created_at;

    GET DIAGNOSTICS v_deleted_count = ROW_COUNT;

    -- Update the message content and edited_at timestamp
    UPDATE public.messages
    SET content = p_new_content,
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

-- 3. Permissions lockdown
REVOKE ALL ON FUNCTION public.edit_user_message(uuid, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.edit_user_message(uuid, text) FROM anon;
GRANT EXECUTE ON FUNCTION public.edit_user_message(uuid, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.edit_user_message(uuid, text) TO service_role;
