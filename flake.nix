{
  description = "fractal-buildings: procedurally generated buildings and cities, a Fabric Minecraft mod";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" ];
      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});
    in
    {
      devShells = forAllSystems (pkgs:
        let
          # Minecraft 26.x requires Java 25. Re-check against https://fabricmc.net/develop/
          # when the target Minecraft version changes.
          jdk = pkgs.jdk25;

          # Native libraries LWJGL dlopen()s at runtime (dev client and client game tests).
          runtimeLibs = with pkgs; [
            libGL
            glfw
            libx11
            libxcursor
            libxrandr
            libxi
            libxxf86vm
            libxext
            libpulseaudio
            openal
            udev
            vulkan-loader # MC 26.x can render via Vulkan; Xvfb GLX lacks the sRGB visual its GL backend wants
            # flite (narrator) deliberately omitted: with no PulseAudio server it aborts the JVM
            # in pa_simple_write when the first-launch accessibility screen speaks.
          ];
        in
        {
          default = pkgs.mkShell {
            packages = [
              jdk
              pkgs.git
              pkgs.jq
              pkgs.ripgrep
              pkgs.poppler-utils # pdftotext / pdftoppm, for reading papers during research
              pkgs.pandoc # docs/research -> EPUB for reading on a phone (scripts/research-epub.lua)
              pkgs.nodejs # viewer/ (TypeScript + three.js); npm comes with it
              pkgs.playwright-driver.browsers # Chromium for viewer screenshots (see PLAYWRIGHT_* below)
              pkgs.silverbullet # for viewing markdown files locally
              pkgs.imagemagick # `import -window root` screenshots of Xvfb (scripts/server-only-test.sh)
              pkgs.vulkan-tools # vulkaninfo / vkcube: which Vulkan device a renderer setting picks (scripts/client-test.sh FB_VK)
            ];

            # Playwright: browsers come from nixpkgs, so the npm `playwright` / `@playwright/test` version in
            # viewer/package.json MUST equal pkgs.playwright-driver.version (currently 1.63.0), or it looks
            # for a browser build that isn't in the store. When bumping nixpkgs, bump the npm pin to match.
            PLAYWRIGHT_BROWSERS_PATH = "${pkgs.playwright-driver.browsers}";
            PLAYWRIGHT_SKIP_VALIDATE_HOST_REQUIREMENTS = "true";

            JAVA_HOME = "${jdk}";

            # /run/opengl-driver/lib is where NixOS puts the Mesa drivers.
            LD_LIBRARY_PATH = "/run/opengl-driver/lib:${pkgs.lib.makeLibraryPath runtimeLibs}";

            # No GPU access here: restrict the Vulkan loader to Mesa's software driver (lavapipe).
            VK_DRIVER_FILES = "${pkgs.mesa}/share/vulkan/icd.d/lvp_icd.${pkgs.stdenv.hostPlatform.uname.processor}.json";

            # ...unless FB_VK_DRIVER_FILES picks another driver. scripts/remote.sh sets it to the
            # worker box's GPU driver (radv), where there's no desktop to share the GPU with.
            shellHook = ''
              if [ -n "''${FB_VK_DRIVER_FILES:-}" ]; then
                # scripts/vk-select.sh still needs lavapipe's ICD for FB_VK=lavapipe.
                export FB_VK_LVP="$VK_DRIVER_FILES"
                export VK_DRIVER_FILES="$FB_VK_DRIVER_FILES"
              fi
            '';

            # Xvnc's pointer device claims relative axes but reports absolute positions in XI2 raw
            # motion events, so SDL3's XInput2 relative mode sees every move as a huge jump (the view
            # spins and pins to the ground). Without XInput2, SDL emulates relative mode by warping.
            SDL_VIDEO_X11_XINPUT2 = "0";

            # SilverBullet (John reads the docs with it; docs/SILVERBULLET.md): gitignore-style
            # patterns, one per line, hiding files from the space: not listed, indexed or in pickers
            # (a direct URL still serves them).
            # Read once at server start: restart SilverBullet after changing them.
            # .claude/ holds agent worktrees, reports and locks; SB must not index or edit them.
            # Source, build and data files are hidden too (John reviews code elsewhere); the
            # space keeps markdown, images, SVG/CSV/text attached to docs, and SB plugs.
            SB_SPACE_IGNORE = pkgs.lib.concatStringsSep "\n" [
              # build output, game run dirs, agent state
              "node_modules/" "build/" "run/" "out/" ".claude/" ".gradle/" "dist/"
              "playwright-report/" "test-results/"
              # source code and src/ trees incl. test resources (SB plugs are .plug.js: re-included below)
              "*.java" "*.kt" "*.ts" "*.tsx" "*.js" "*.mjs" "*.cjs" "*.py" "*.lua" "*.sh" "*.bat"
              "*.html" "*.css" "src/"
              "!*.plug.js"
              # build config, lockfiles, wrappers
              "*.gradle" "*.kts" "*.properties" "gradlew" "*.jar" "*.nix" "*.lock"
              ".gitignore" ".gitattributes" ".envrc"
              # generator data: fixtures, presets, recorded terrain, logs
              "*.json" "*.gz" "*.tsv" "*.log"
              ""
            ];
          };
        });
    };
}
