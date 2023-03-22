<#import "sd-template-lib" as lib>

<p>Добрый день!</p>
<p>Обращение <a href="${link.getRecordLink(docRef)}" target="_blank">SD-${docNum}</a> присвоен статус "Закрыто".</p>

<@lib.reply_message/>
