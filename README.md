
# TikTok Minimal

Standalone native Android short-video platform built from scratch with Kotlin and Jetpack Compose.

The project does not copy TikTok source code, private assets, internal services, internal APIs, or branding.

## Android stack

- Kotlin 2.3.21
- Android Gradle Plugin 8.13.2
- Jetpack Compose BOM 2026.09.00
- Material 3 1.4.0
- AndroidX Media3 1.11.1 with HLS support
- Coil 3.6.3
- Supabase Kotlin client 3.8.0
- Ktor Android client 3.6.0

## Backend

The current Supabase project is used because the connected Supabase free organization is already at its active free-project limit. The project used by this app was verified to have an empty public application schema before the TikTok Minimal schema was applied.

The database contains:

profiles
videos
likes
comments
follows
shares
notifications

It also contains feed_videos and video_comment_feed views, Row Level Security policies, database triggers for counters and notifications, and dedicated Storage buckets named videos and avatars.

The existing user-files bucket was left untouched.

## Configuration

The app reads these values from local.properties first, then Gradle properties, then environment variables:

SUPABASE_URL
SUPABASE_PUBLISHABLE_KEY

Example local.properties values:

SUPABASE_URL=https://your-project.supabase.co
SUPABASE_PUBLISHABLE_KEY=your-publishable-key

Do not commit local.properties or server-side Supabase secrets.

## Authentication

Email and password sign-up/sign-in are implemented with persistent Supabase sessions.

Google OAuth is wired to the native callback scheme:

tiktokminimal://auth/callback

The Supabase project must have a real Google OAuth client ID and secret configured before Google sign-in can complete. The repository intentionally does not invent or store those credentials.

## Media

The feed uses AndroidX Media3 ExoPlayer with hardware-accelerated rendering through the Android media stack, lifecycle-aware pause/resume, a bounded local cache, and next-item media preloading.

Large uploads use the Supabase Kotlin resumable upload API with progress reporting. The Android app itself does not impose a small artificial video-size limit; actual service-plan and Storage limits still apply.

## Build

GitHub Actions runs unit tests, Android lint, assembles a debug APK, checks that the APK is non-empty, and uploads it as an artifact.

There are no production video seeds in the repository. An empty feed is a valid initial state until users upload real content.
