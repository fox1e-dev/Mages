{
  description = "Mages Nix packages";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs =
    { self, nixpkgs }:
    let
      lib = nixpkgs.lib;

      hashes = builtins.fromJSON (builtins.readFile ./hashes.json);

      allVersions = lib.sort builtins.compareVersions (builtins.attrNames hashes);

      selected =
        if allVersions == [ ] then
          throw "nix/hashes.json is empty; run the Publish Nix Flake workflow"
        else
          builtins.elemAt allVersions ((builtins.length allVersions) - 1);

      release = hashes.${selected};

      systems = [
        "x86_64-linux"
        "aarch64-linux"
      ];

      forAllSystems =
        f:
        lib.genAttrs systems (
          system: f {
            inherit system;
            pkgs = nixpkgs.legacyPackages.${system};
          }
        );
    in
    {
      packages = forAllSystems (
        { pkgs, ... }:
        let
          artifact = release.${pkgs.stdenv.hostPlatform.system};
        in
        rec {
          mages = pkgs.stdenvNoCC.mkDerivation {
            pname = "mages";
            version = selected;

            src = pkgs.fetchurl {
              url = "https://github.com/mlm-games/mages/releases/download/${release.tag}/${artifact.file}";
              hash = artifact.hash;
            };

            dontFixup = true;

            nativeBuildInputs = [ pkgs.makeWrapper ];

            unpackPhase = ''
              runHook preUnpack
              cp $src ./source
              chmod +x ./source
              ./source --appimage-extract
              runHook postUnpack
            '';

            sourceRoot = "squashfs-root";

            installPhase = ''
              runHook preInstall

              mkdir -p "$out/libexec/mages"
              cp -r . "$out/libexec/mages/"

              makeWrapper "$out/libexec/mages/AppRun" "$out/bin/mages"

              install -Dm644 io.github.mlm_games.mages.desktop \
                "$out/share/applications/io.github.mlm_games.mages.desktop"
              substituteInPlace "$out/share/applications/io.github.mlm_games.mages.desktop" \
                --replace-fail 'Exec=Mages %U' 'Exec=mages %U' \
                --replace-fail 'Icon=mages' 'Icon=io.github.mlm_games.mages'

              install -Dm644 lib/Mages.png \
                "$out/share/icons/hicolor/512x512/apps/io.github.mlm_games.mages.png"

              install -Dm644 usr/share/metainfo/io.github.mlm_games.mages.metainfo.xml \
                "$out/share/metainfo/io.github.mlm_games.mages.metainfo.xml"

              runHook postInstall
            '';

            meta = with lib; {
              description = "Matrix client with a tray notifier";
              homepage = "https://github.com/mlm-games/mages";
              license = licenses.agpl3Only;
              mainProgram = "mages";
              platforms = [
                "x86_64-linux"
                "aarch64-linux"
              ];
            };
          };

          default = mages;
        }
      );
    };
}
