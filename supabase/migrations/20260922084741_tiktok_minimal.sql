
-- TikTok Minimal backend schema.
-- This migration is idempotent and can be reapplied to a clean Supabase project.

create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text not null,
  avatar_url text,
  bio text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint profiles_username_length check (char_length(username) between 3 and 30),
  constraint profiles_username_format check (username ~ '^[A-Za-z0-9_\.]+$')
);

create unique index if not exists profiles_username_lower_uidx
  on public.profiles (lower(username));

create table if not exists public.videos (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  storage_path text not null unique,
  caption text not null default '',
  hashtags text[] not null default '{}',
  duration_ms bigint,
  width integer,
  height integer,
  status text not null default 'published'
    check (status in ('processing','published','failed')),
  like_count bigint not null default 0,
  comment_count bigint not null default 0,
  share_count bigint not null default 0,
  created_at timestamptz not null default now()
);
create index if not exists videos_created_at_id_idx
  on public.videos (created_at desc, id desc);
create index if not exists videos_user_created_idx
  on public.videos (user_id, created_at desc);
create index if not exists videos_hashtags_gin_idx
  on public.videos using gin (hashtags);

create table if not exists public.likes (
  user_id uuid not null references public.profiles(id) on delete cascade,
  video_id uuid not null references public.videos(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, video_id)
);
create index if not exists likes_video_idx on public.likes(video_id);

create table if not exists public.comments (
  id uuid primary key default gen_random_uuid(),
  video_id uuid not null references public.videos(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  body text not null check (char_length(btrim(body)) between 1 and 2000),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists comments_video_created_idx
  on public.comments(video_id, created_at desc);

create table if not exists public.follows (
  follower_id uuid not null references public.profiles(id) on delete cascade,
  following_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (follower_id, following_id),
  constraint follows_no_self check (follower_id <> following_id)
);
create index if not exists follows_following_idx on public.follows(following_id);

create table if not exists public.shares (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  video_id uuid not null references public.videos(id) on delete cascade,
  created_at timestamptz not null default now()
);
create index if not exists shares_video_created_idx on public.shares(video_id, created_at desc);

create table if not exists public.notifications (
  id uuid primary key default gen_random_uuid(),
  recipient_id uuid not null references public.profiles(id) on delete cascade,
  actor_id uuid not null references public.profiles(id) on delete cascade,
  type text not null check (type in ('follow','like','comment','share')),
  video_id uuid references public.videos(id) on delete cascade,
  comment_id uuid references public.comments(id) on delete cascade,
  created_at timestamptz not null default now(),
  read_at timestamptz
);
create index if not exists notifications_recipient_created_idx
  on public.notifications(recipient_id, created_at desc);

create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists profiles_set_updated_at on public.profiles;
create trigger profiles_set_updated_at
before update on public.profiles for each row
execute function public.set_updated_at();

drop trigger if exists comments_set_updated_at on public.comments;
create trigger comments_set_updated_at
before update on public.comments for each row
execute function public.set_updated_at();

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public
as $$
declare
  base_username text;
begin
  base_username := lower(regexp_replace(
    coalesce(
      new.raw_user_meta_data->>'user_name',
      new.raw_user_meta_data->>'name',
      split_part(coalesce(new.email, 'user'), '@', 1),
      'user'
    ),
    '[^A-Za-z0-9_\.]+', '_', 'g'
  ));
  base_username := left(base_username, 22);
  if char_length(base_username) < 3 then base_username := 'user'; end if;

  begin
    insert into public.profiles(id, username, avatar_url)
    values (new.id, base_username, new.raw_user_meta_data->>'avatar_url');
  exception when unique_violation then
    insert into public.profiles(id, username, avatar_url)
    values (
      new.id,
      left(base_username, 14) || '_' || substr(replace(new.id::text, '-', ''), 1, 8),
      new.raw_user_meta_data->>'avatar_url'
    );
  end;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users for each row
execute function public.handle_new_user();

create or replace function public.update_video_like_count()
returns trigger language plpgsql as $$
begin
  if tg_op = 'INSERT' then
    update public.videos set like_count = like_count + 1 where id = new.video_id;
    if exists (
      select 1 from public.videos v
      where v.id = new.video_id and v.user_id <> new.user_id
    ) then
      insert into public.notifications(recipient_id, actor_id, type, video_id)
      select v.user_id, new.user_id, 'like', v.id
      from public.videos v where v.id = new.video_id;
    end if;
    return new;
  end if;

  update public.videos
  set like_count = greatest(0, like_count - 1)
  where id = old.video_id;
  return old;
end;
$$;

drop trigger if exists likes_count_trigger on public.likes;
create trigger likes_count_trigger
after insert or delete on public.likes for each row
execute function public.update_video_like_count();

create or replace function public.update_video_comment_count()
returns trigger language plpgsql as $$
begin
  if tg_op = 'INSERT' then
    update public.videos set comment_count = comment_count + 1 where id = new.video_id;
    if exists (
      select 1 from public.videos v
      where v.id = new.video_id and v.user_id <> new.user_id
    ) then
      insert into public.notifications(
        recipient_id, actor_id, type, video_id, comment_id
      )
      select v.user_id, new.user_id, 'comment', v.id, new.id
      from public.videos v where v.id = new.video_id;
    end if;
    return new;
  end if;

  update public.videos
  set comment_count = greatest(0, comment_count - 1)
  where id = old.video_id;
  return old;
end;
$$;

drop trigger if exists comments_count_trigger on public.comments;
create trigger comments_count_trigger
after insert or delete on public.comments for each row
execute function public.update_video_comment_count();

create or replace function public.update_video_share_count()
returns trigger language plpgsql as $$
begin
  update public.videos set share_count = share_count + 1 where id = new.video_id;
  return new;
end;
$$;

drop trigger if exists shares_count_trigger on public.shares;
create trigger shares_count_trigger
after insert on public.shares for each row
execute function public.update_video_share_count();

create or replace function public.notify_follow()
returns trigger language plpgsql as $$
begin
  insert into public.notifications(recipient_id, actor_id, type)
  values (new.following_id, new.follower_id, 'follow');
  return new;
end;
$$;

drop trigger if exists follows_notification_trigger on public.follows;
create trigger follows_notification_trigger
after insert on public.follows for each row
execute function public.notify_follow();

alter table public.profiles enable row level security;
alter table public.videos enable row level security;
alter table public.likes enable row level security;
alter table public.comments enable row level security;
alter table public.follows enable row level security;
alter table public.shares enable row level security;
alter table public.notifications enable row level security;

do $$
begin
  if not exists (select 1 from pg_policies where schemaname='public' and tablename='profiles' and policyname='profiles_select_public') then
    create policy profiles_select_public on public.profiles for select using (true);
  end if;
  if not exists (select 1 from pg_policies where schemaname='public' and tablename='profiles' and policyname='profiles_insert_own') then
    create policy profiles_insert_own on public.profiles for insert with check (auth.uid() = id);
  end if;
  if not exists (select 1 from pg_policies where schemaname='public' and tablename='profiles' and policyname='profiles_update_own') then
    create policy profiles_update_own on public.profiles for update using (auth.uid() = id) with check (auth.uid() = id);
  end if;

  if not exists (select 1 from pg_policies where schemaname='public' and tablename='videos' and policyname='videos_select_published') then
    create policy videos_select_published on public.videos for select using (status='published');
  end if;
  if not exists (select 1 from pg_policies where schemaname='public' and tablename='videos' and policyname='videos_insert_own') then
    create policy videos_insert_own on public.videos for insert with check (auth.uid() = user_id);
  end if;
  if not exists (select 1 from pg_policies where schemaname='public' and tablename='videos' and policyname='videos_update_own') then
    create policy videos_update_own on public.videos for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
  end if;
  if not exists (select 1 from pg_policies where schemaname='public' and tablename='videos' and policyname='videos_delete_own') then
    create policy videos_delete_own on public.videos for delete using (auth.uid() = user_id);
  end if;

  if not exists (select 1 from pg_policies where schemaname='public' and tablename='likes' and policyname='likes_select_public') then
    create policy likes_select_public on public.likes for select using (true);
  end if;
  if not exists (select 1 from pg_policies where schemaname='public' and tablename='likes' and policyname='likes_insert_own') then
    create policy likes_insert_own on public.likes for insert with check (auth.uid() = user_id);
  end if;
  if not exists (select 1 from pg_policies where schemaname='public' and tablename='likes' and policyname='likes_delete_own') then
    create policy likes_delete_own on public.likes for delete using (auth.uid() = user_id);
  end if;

  if not exists (select 1 from pg_policies where schemaname='public' and tablename='comments' and policyname='comments_select_public') then
    create policy comments_select_public on public.comments for select using (true);
  end if;
  if not exists (select 1 from pg_policies where schemaname='public' and tablename='comments' and policyname='comments_insert_own') then
    create policy comments_insert_own on public.comments for insert with check (auth.uid() = user_id);
  end if;
  if not exists (select 1 from pg_policies where schemaname='public' and tablename='comments' and policyname='comments_update_own') then
    create policy comments_update_own on public.comments for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
  end if;
  if not exists (select 1 from pg_policies where schemaname='public' and tablename='comments' and policyname='comments_delete_own') then
    create policy comments_delete_own on public.comments for delete using (auth.uid() = user_id);
  end if;

  if not exists (select 1 from pg_policies where schemaname='public' and tablename='follows' and policyname='follows_select_public') then
    create policy follows_select_public on public.follows for select using (true);
  end if;
  if not exists (select 1 from pg_policies where schemaname='public' and tablename='follows' and policyname='follows_insert_own') then
    create policy follows_insert_own on public.follows for insert with check (auth.uid() = follower_id);
  end if;
  if not exists (select 1 from pg_policies where schemaname='public' and tablename='follows' and policyname='follows_delete_own') then
    create policy follows_delete_own on public.follows for delete using (auth.uid() = follower_id);
  end if;

  if not exists (select 1 from pg_policies where schemaname='public' and tablename='shares' and policyname='shares_select_public') then
    create policy shares_select_public on public.shares for select using (true);
  end if;
  if not exists (select 1 from pg_policies where schemaname='public' and tablename='shares' and policyname='shares_insert_own') then
    create policy shares_insert_own on public.shares for insert with check (auth.uid() = user_id);
  end if;

  if not exists (select 1 from pg_policies where schemaname='public' and tablename='notifications' and policyname='notifications_select_own') then
    create policy notifications_select_own on public.notifications for select using (auth.uid() = recipient_id);
  end if;
  if not exists (select 1 from pg_policies where schemaname='public' and tablename='notifications' and policyname='notifications_update_own') then
    create policy notifications_update_own on public.notifications for update using (auth.uid() = recipient_id) with check (auth.uid() = recipient_id);
  end if;
end $$;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('videos', 'videos', true, 5368709120, array['video/mp4','video/webm','video/quicktime']),
  ('avatars', 'avatars', true, 10485760, array['image/jpeg','image/png','image/webp'])
on conflict (id) do update
set public=excluded.public,
    file_size_limit=excluded.file_size_limit,
    allowed_mime_types=excluded.allowed_mime_types;

do $$
begin
  if not exists (select 1 from pg_policies where schemaname='storage' and tablename='objects' and policyname='videos_insert_own_folder') then
    create policy videos_insert_own_folder on storage.objects
      for insert to authenticated
      with check (bucket_id='videos' and (storage.foldername(name))[1]=auth.uid()::text);
  end if;
  if not exists (select 1 from pg_policies where schemaname='storage' and tablename='objects' and policyname='videos_update_own_folder') then
    create policy videos_update_own_folder on storage.objects
      for update to authenticated
      using (bucket_id='videos' and (storage.foldername(name))[1]=auth.uid()::text)
      with check (bucket_id='videos' and (storage.foldername(name))[1]=auth.uid()::text);
  end if;
  if not exists (select 1 from pg_policies where schemaname='storage' and tablename='objects' and policyname='videos_delete_own_folder') then
    create policy videos_delete_own_folder on storage.objects
      for delete to authenticated
      using (bucket_id='videos' and (storage.foldername(name))[1]=auth.uid()::text);
  end if;

  if not exists (select 1 from pg_policies where schemaname='storage' and tablename='objects' and policyname='avatars_insert_own_folder') then
    create policy avatars_insert_own_folder on storage.objects
      for insert to authenticated
      with check (bucket_id='avatars' and (storage.foldername(name))[1]=auth.uid()::text);
  end if;
  if not exists (select 1 from pg_policies where schemaname='storage' and tablename='objects' and policyname='avatars_update_own_folder') then
    create policy avatars_update_own_folder on storage.objects
      for update to authenticated
      using (bucket_id='avatars' and (storage.foldername(name))[1]=auth.uid()::text)
      with check (bucket_id='avatars' and (storage.foldername(name))[1]=auth.uid()::text);
  end if;
  if not exists (select 1 from pg_policies where schemaname='storage' and tablename='objects' and policyname='avatars_delete_own_folder') then
    create policy avatars_delete_own_folder on storage.objects
      for delete to authenticated
      using (bucket_id='avatars' and (storage.foldername(name))[1]=auth.uid()::text);
  end if;
end $$;

create or replace view public.feed_videos
with (security_invoker = true)
as
select
  v.id, v.user_id, v.storage_path, v.caption, v.hashtags,
  v.duration_ms, v.width, v.height,
  v.like_count, v.comment_count, v.share_count,
  v.created_at, p.username, p.avatar_url
from public.videos v
join public.profiles p on p.id = v.user_id
where v.status='published';

grant select on public.feed_videos to anon, authenticated;

create or replace view public.video_comment_feed
with (security_invoker = true)
as
select c.id, c.video_id, c.user_id, c.body, c.created_at, p.username, p.avatar_url
from public.comments c
join public.profiles p on p.id = c.user_id;

grant select on public.video_comment_feed to anon, authenticated;

grant select on public.profiles, public.videos, public.likes, public.comments,
  public.follows, public.shares, public.notifications to anon, authenticated;
grant insert, update, delete on public.profiles, public.videos, public.likes,
  public.comments, public.follows, public.shares to authenticated;
grant select, update on public.notifications to authenticated;
