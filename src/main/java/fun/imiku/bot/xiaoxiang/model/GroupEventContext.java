package fun.imiku.bot.xiaoxiang.model;

import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import fun.imiku.bot.xiaoxiang.utils.MessageUtil;
import lombok.Getter;

import java.util.List;

@Getter
public class GroupEventContext {
    private final XXBot xxBot;
    private final GroupMessageEvent event;
    private final String content;
    private final List<String> split;
    private final String fContent;
    private final Long groupId;
    private final Long userId;

    public GroupEventContext(
            XXBot bot,
            GroupMessageEvent event,
            String plainText
    ) {
        this.xxBot = bot;
        this.event = event;
        this.content = plainText.trim();
        this.split = MessageUtil.splitContent(this.content);
        this.fContent = MessageUtil.getEffectiveMessage(this.content);
        this.groupId = event.getGroupId();
        this.userId = event.getUserId();
    }
}
