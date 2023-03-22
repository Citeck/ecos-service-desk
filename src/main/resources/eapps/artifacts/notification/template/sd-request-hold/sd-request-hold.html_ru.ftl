<#import "sd-template-lib" as lib>

<p>Добрый день!</p>
<p>Обращению <a href="${link.getRecordLink(docRef)}" target="_blank">SD-${docNum}</a> присвоен статус "На удержании"</p>

<@lib.reply_message/>
