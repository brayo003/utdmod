from PIL import Image, ImageDraw

# Signal Probe
img = Image.new('RGB', (16, 16), (30, 144, 255))  # Blue
draw = ImageDraw.Draw(img)
draw.line((8, 2, 8, 14), fill=(255, 255, 255))  # White vertical
draw.line((2, 8, 14, 8), fill=(255, 255, 255))  # White horizontal
draw.ellipse((4, 4, 12, 12), outline=(255, 255, 255))  # White circle
img.save('src/main/resources/assets/utdmod/textures/item/signal_probe.png')

# Warding Crystal
img = Image.new('RGB', (16, 16), (138, 43, 226))  # Purple
draw = ImageDraw.Draw(img)
draw.polygon([(8, 2), (14, 8), (8, 14), (2, 8)], fill=(186, 85, 211))  # Diamond
img.save('src/main/resources/assets/utdmod/textures/item/warding_crystal.png')

# Ritual Block
img = Image.new('RGB', (16, 16), (93, 109, 126))  # Gray
draw = ImageDraw.Draw(img)
draw.rectangle((4, 4, 12, 12), fill=(241, 196, 15))  # Yellow square
draw.line((8, 5, 8, 11), fill=(183, 149, 11), width=1)  # Cross
draw.line((5, 8, 11, 8), fill=(183, 149, 11), width=1)
img.save('src/main/resources/assets/utdmod/textures/block/ritual_block.png')
print("Textures created successfully!")
