<#import "sd-template-lib" as lib>

<p>Добрый день!</p>
<p>Обращению <a href="${link.getRecordLink(docRef)}" target="_blank">SD-${docNum}</a> присвоен статус "Закрыто".</p>
<p>В случае возникновения дополнительных вопросов просим создать новое обращение.</p>

<@lib.reply_message/>
