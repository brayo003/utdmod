#!/bin/bash

# --- Configuration ---
RESOURCE_PATH="src/main/resources/assets/utdmod"

# Stable Asset Links (Using a public Minecraft asset mirror)
LINK_STONE_PNG="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.20.4/assets/minecraft/textures/block/stone.png"
LINK_DIAMOND_SWORD="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.20.4/assets/minecraft/textures/item/diamond_sword.png"
LINK_CUBE_ALL="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.20.4/assets/minecraft/models/block/cube_all.json"
LINK_HANDHELD="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.20.4/assets/minecraft/models/item/handheld.json"

# 1. Create all necessary directories
echo "Creating necessary directories..."
mkdir -p $RESOURCE_PATH/{textures/block,textures/item,models/block,models/item,sounds,lang,blockstates}

# 2. Download Placeholder Textures (Fixing 404 errors)
echo "Downloading placeholder textures..."
wget -O $RESOURCE_PATH/textures/block/ritual_block.png $LINK_STONE_PNG
wget -O $RESOURCE_PATH/textures/item/magic_wand.png $LINK_DIAMOND_SWORD

# 3. Download Placeholder Models (Fixing 404 errors)
echo "Downloading placeholder models..."
wget -O $RESOURCE_PATH/models/block/ritual_block.json $LINK_CUBE_ALL
wget -O $RESOURCE_PATH/models/item/magic_wand.json $LINK_HANDHELD

# 4. Create Sound File Placeholder
echo "Creating placeholder sound file (magic.ogg)..."
touch $RESOURCE_PATH/sounds/magic.ogg

# 5. Create Language File (Fixing corrupted command)
echo "Creating language file (en_us.json)..."
cat > $RESOURCE_PATH/lang/en_us.json << 'EOL'
{
  "block.utdmod.ritual_block": "Ritual Block",
  "item.utdmod.magic_wand": "Magic Wand",
  "utdmod.sound.magic": "Magic Sound"
}
EOL

# 6. Create Blockstate File (Fixing corrupted command)
echo "Creating blockstate file (ritual_block.json)..."
cat > $RESOURCE_PATH/blockstates/ritual_block.json << 'EOL'
{
  "variants": {
    "": { "model": "utdmod:block/ritual_block" }
  }
}
EOL

echo "---"
echo "Resource setup complete. All files have been created or downloaded successfully."
