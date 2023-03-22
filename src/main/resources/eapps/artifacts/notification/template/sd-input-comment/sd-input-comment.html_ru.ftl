<#import "sd-template-lib" as lib>

<p>В заявке <a href="${link.getRecordLink(docRef)}" target="_blank">SD-${docNum}</a> ${commenter!''} добавил
    комментарий:</p>

<blockquote>
    ${comment!''}
</blockquote>

<@lib.reply_message/>
