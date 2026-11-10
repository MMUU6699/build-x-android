-- ROLLBACK_MESSAGE_ACTIONS.sql
-- Safely reverts 20261110000002_message_actions.sql

DROP FUNCTION IF EXISTS public.edit_user_message(uuid, text);
ALTER TABLE public.messages DROP COLUMN IF EXISTS edited_at;
