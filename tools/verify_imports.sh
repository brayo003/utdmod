#!/bin/bash
set -e

echo "Checking for old imports..."

grep -R "com.mojang.blaze3d.vertex.PoseStack" -n src && echo "FOUND BAD IMPORT" && exit 1
grep -R "net.minecraft.client.Minecraft;" -n src && echo "FOUND BAD IMPORT" && exit 1
grep -R "GuiComponent" -n src && echo "FOUND BAD IMPORT" && exit 1
grep -R "DrawableHelper" -n src && echo "FOUND BAD IMPORT" && exit 1
grep -R "ServerPlayer;" -n src && echo "FOUND BAD IMPORT" && exit 1

echo "All clear."
