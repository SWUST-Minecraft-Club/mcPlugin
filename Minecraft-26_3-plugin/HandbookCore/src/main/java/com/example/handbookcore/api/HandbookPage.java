package com.example.handbookcore.api;

import java.util.List;
import java.util.Objects;

/** 逻辑页；超出纸页空间会继续排到后面的纸页，不裁掉内容。 */
public record HandbookPage(String title, List<HandbookBlock> blocks) {
    public HandbookPage {
        Objects.requireNonNull(title, "title");
        blocks = List.copyOf(blocks);
        if (blocks.size() > 256) throw new IllegalArgumentException("每个逻辑页最多 256 个内容块");
    }
    public static HandbookPage text(String title, String text) {
        return new HandbookPage(title, List.of(HandbookBlock.text(text)));
    }
}
