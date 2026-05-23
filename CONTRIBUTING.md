**Developer Quick Start**

Follow these steps to set up a development machine that can build and run the app locally (Windows-focused commands shown). Share this with teammates.

- **Prereqs:**
  - Install Java JDK 11 or newer (OpenJDK/Oracle)
  - Install Android Studio and Android SDK (install Platform 36)
  - Install Node.js (v16+ or v18+) and npm
  - Install Git and configure your name/email

- **Clone and prepare**
  - Clone the repo and enter it:
    git clone https://github.com/7yn-ril/ZejiosCafeSE.git
    cd ZejiosCafeSE
  - Fetch and stay up-to-date before you start working:
    git pull --rebase origin develop

- **Project deps**
  - Install JS deps (for Supabase functions/tools):
    npm ci
  - Use the Gradle wrapper to build the Android app (Windows):
    .\gradlew.bat :app:assembleDebug

- **Supabase CLI / local functions**
  - Do NOT add CLI binaries to the repo. Install Supabase CLI via a package manager:
    - Windows (scoop/chocolatey) or follow the official docs: https://supabase.com/docs/guides/cli
  - If you need to run local Supabase functions, use the installed CLI from your PATH.

- **local.properties (required per-machine)**
  - Copy the example and edit your values (sdk path, Supabase creds, local login):
    copy local.properties.example local.properties
    (then open `local.properties` and set `sdk.dir`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `LOGIN_USERNAME`, `LOGIN_PASSWORD_SHA256`)
  - Important: do NOT commit `local.properties`. Keep it private. The repo contains `local.properties.example` as the template.

- **What to avoid committing**
  - Do not commit `node_modules/` or any executable binaries (e.g., supabase.exe). Large binaries cause push failures on GitHub.
  - If you must track large binaries, use Git LFS and coordinate with the team.

- **Common commands for development**
  - Build and run on Pixel tablet helper (Windows PowerShell):
    .\run_pixel_tablet.ps1
  - Cold-boot emulator to fix DNS issues:
    .\run_tablet.ps1 -ColdBoot
  - Run unit tests:
    .\gradlew.bat :app:testDebugUnitTest

- **If your push is rejected for large files**
  - Do not force-push blindly. Remove large files from your commit and add them to `.gitignore`, or use Git LFS:
    git rm --cached path/to/large-file
    echo "node_modules/" >> .gitignore
    git add .gitignore
    git commit -m "remove large binaries and ignore node_modules"

- **Notes for reviewers / teammates**
  - Share `local.properties.example` values privately (do not push real credentials).
  - If you receive a push error about large files, ping the author — we will remove the binary from history and push a cleaned commit.

If you want, I can also add a short `setup-dev.sh`/`setup-dev.ps1` script that automates these local steps. Tell me which OS your teammates use and I'll add it.
