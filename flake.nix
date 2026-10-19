{
  description = "Better Watch Calendar: Wear OS next-meeting complication + phone companion";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    android-nixpkgs = {
      url = "github:tadfisher/android-nixpkgs";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    { nixpkgs, android-nixpkgs, ... }:
    let
      system = "x86_64-linux";

      # Unfree (Android Studio) and SDK licenses are accepted here only, not globally.
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };

      jdk = pkgs.jdk25;

      androidSdk = android-nixpkgs.sdk.${system} (
        sdk: with sdk; [
          platform-tools
          build-tools-37-0-0
          platforms-android-37-0
          cmdline-tools-latest
          emulator
          # Phone emulator (Play Store image so a test Google account can be added).
          system-images-android-37-0-google-apis-playstore-x86-64
          # Wear OS emulator. The real Pixel Watch 2 reports API 37, so match it.
          system-images-android-37-0-android-wear-signed-x86-64
        ]
      );
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          jdk
          androidSdk
          pkgs.android-studio
          pkgs.ktfmt
          pkgs.just
        ];

        JAVA_HOME = jdk.home;
        ANDROID_HOME = "${androidSdk}/share/android-sdk";
        ANDROID_SDK_ROOT = "${androidSdk}/share/android-sdk";
      };

      formatter.${system} = pkgs.nixfmt;
    };
}
