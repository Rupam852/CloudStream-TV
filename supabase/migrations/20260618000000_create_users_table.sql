-- Migration to create the permanent users table
CREATE TABLE IF NOT EXISTS public.users (
    email TEXT PRIMARY KEY,
    name TEXT,
    profile_picture TEXT,
    refresh_token TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Enable Row Level Security (RLS) for privacy
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;

-- Allow Service Role key to do everything (standard for administrative bypass)
-- No public SELECT/INSERT/UPDATE policies are needed because our Edge Function uses the Service Role key to write and read.
