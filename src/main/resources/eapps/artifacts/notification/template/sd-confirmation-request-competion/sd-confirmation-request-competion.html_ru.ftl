<#import "sd-template-lib" as lib>

<p>Добрый день!</p>
<p>Обращению <a href="${link.getRecordLink(docRef)}" target="_blank">SD-${docNum}</a> присвоен статус "Подтверждение
    выполнения заявки"</p>

<@lib.reply_message/>
