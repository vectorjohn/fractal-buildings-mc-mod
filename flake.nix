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

            # Xvnc's pointer device claims relative axes but reports absolute positions in XI2 raw
            # motion events, so SDL3's XInput2 relative mode sees every move as a huge jump (the view
            # spins and pins to the ground). Without XInput2, SDL emulates relative mode by warping.
            SDL_VIDEO_X11_XINPUT2 = "0";

            # SilverBullet (John reads the docs with it; docs/SILVERBULLET.md): gitignore-style
            # patterns, one per line, hiding build output and game run dirs from the space.
            SB_SPACE_IGNORE = "node_modules/\nbuild/\nrun/\nout/\n";
          };
        });
    };
}
