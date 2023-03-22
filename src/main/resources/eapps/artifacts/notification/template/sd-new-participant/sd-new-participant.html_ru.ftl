<#import "sd-template-lib" as lib>

<p>Добрый день!</p>
<p>Вас привлекли к заявке <a href="${link.getRecordLink(docRef)}" target="_blank">SD-${docNum}</a> c комментарием:</p>

<blockquote>
    ${additionalParticipantComments?last!''}
</blockquote>

<@lib.reply_message/>

