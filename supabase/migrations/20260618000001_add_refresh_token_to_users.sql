-- Migration to add refresh_token column to users table
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS refresh_token TEXT;
