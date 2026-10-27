# 生成记录

本目录书本与书签使用 Codex 内置图像生成工具制作，透明背景由生成器直接提供。参考来自用户批准的概念图，未复制 Patchouli 贴图。

## 通用书本基础材质

```text
Use case: precise-object-edit.
Asset type: production base texture for the existing Minecraft handbook.
Edit target: image 1 is our approved pixel-art empty-text book texture. Preserve its beautiful pixel-art material, ivory paper, leather cover, center stitching, golden wheat title ribbon, outside right-edge colorful bookmarks, and bottom arrow style. We need a GENERIC book skin which can be used by any plugin, with all the content rendered at runtime.
CHANGES:
1. Remove ALL four large category illustrations on the right page, remove the large farm illustration on the left, remove the small wheat sprigs in the lower left page interior. Fill these areas with clean matching ivory parchment.
2. Keep and refine the integrated blank top-left wheat ribbon. Do not add text. Keep thin sophisticated outlines, tonal material texture, bright gold wheat sprigs, folded outer end and pointed spine-side end. No flat rectangle replacement.
3. Right-edge bookmarks: keep four blank stitched colorful leather tabs (forest green, terra-cotta brown, ochre gold, burgundy); REMOVE their emblems so we can render dynamic icons.
4. At bottom LEFT page, draw THREE small separated leather bookmark-like buttons, integrated consistently with the book's art, aligned horizontally and comfortably inset from the paper edges. Muted warm brown outer edge, delicately shaded gold rim, LIGHT IVORY central label area empty. The three label-safe areas must be wide enough for 2-3 Chinese characters. All three identical in size and material. No lettering.
5. At bottom RIGHT page, integrate two small gold-brown triangular page-turn arrows near the outer ends of that page's footer and one tiny blank page-number ornament centered between them. Remove the existing lower-left-page arrow and all existing bottom ornamentation which conflicts with these controls.
6. Preserve the right page's upper ornamental gold rule and an elegant clean empty title area above it. Remove the horizontal ornamental rule from the lower right page so arbitrary long content has space.
Layout: Entire book including tabs fully visible, center spine at about 49% of canvas, frontal flat view. Main book body fills x4%..94%,y3%..97%; tabs x93%..99%. Left ribbon x2%..47%, y9%..22%. Left main content blank x11%..44%, y28%..82%; right main content blank x55%..88%,y26%..82%. Left footer controls three centers x15%,27%,39%,y89%, width10% height7% each. Right previous/next arrow centers x58%,85%,y89%; blank indicator at x72%,y89%.
Constraints: rich detailed crisp pixel art in the SAME style and quality as input, no smooth vector shapes, no giant flat rectangular boxes, no category card frames. No text, letters, numbers, icons, pseudo-text, farm, trees, houses, items, logos, watermark or world screenshot. All inner content zones clear matching paper. Preserve real transparent alpha outside book.
```

## 移除固定书签，供运行时动态绘制

```text
Use case: precise-object-edit.
Edit target: the supplied newly generated pixel-art book base.
Change ONLY the four colorful leather bookmark tabs on the outside RIGHT edge: remove all four of them completely, including the small folded tails and their dark drop shadows projecting outside the cover. Restore the book's right cover edge and page-edge silhouette underneath them, consistent with the unoccluded bottom-right cover. Outside silhouette is genuinely transparent. We will draw dynamic bookmarks separately at runtime.
Preserve everything else pixel-for-pixel as closely as possible: the canvas proportions, spine location, parchment paper, gold wheat title ribbon, top-right ornamental rule, three beautiful bottom-left buttons, both right-page arrows and central footer ornament. All runtime content areas stay blank, no text or new artwork. No other changes.
```

## 独立书签状态图集

```text
Use case: ui-mockup.
Asset type: reusable transparent BOOKMARK BUTTON SPRITE SHEET for our pixel-art Minecraft handbook.
Reference image: matching material/style reference ONLY, the supplied open handbook. Do not draw a book.
Create precisely EIGHT separate horizontal leather bookmark tabs arranged in a 4-column by 2-row REGULAR GRID on genuine transparency. Each grid cell has exactly equal dimensions and each bookmark occupies the same local position and height within its cell, with ample transparent gap between all sprites.
COLORS by column, both rows: forest green, terra-cotta brown, ochre gold, burgundy red.
TOP row: normal state. BOTTOM row: selected state of corresponding tab, slightly brighter gold edge and a small gold clasp at its left attachment.
Shape: wide rectangular tab projecting horizontally to the right from a book edge. Width about 1.15 times its height. Straight left attachment, beveled right corners, a folded short triangular tail at lower-right. Rich textured leather face, thin two-tone gold stitching border, tiny controlled highlights and dark pixel edge. Empty center area covers middle 60% for a dynamic 16px item icon. No emblem, no lettering.
Exact same frontal flat crisp detailed PIXEL-ART craftsmanship as reference's colorful side bookmarks and gold-brown page arrows; beautiful cohesive material, not smooth vector UI or flat boxes. Pixel clusters consistent across all eight variants. No book, no paper background, no text, numbers, icon, title or watermark. Output only the eight tabs on true transparent alpha.
```
