-- Migration: Add is_locked to public.conversations for private/locked chat feature
ALTER TABLE public.conversations
    ADD COLUMN IF NOT EXISTS is_locked boolean NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_conversations_user_locked
    ON public.conversations (user_id, is_locked) WHERE is_locked = true;
