<#import "sd-template-lib" as lib>

<p>Добрый день!</p>
<p>Вам назначена задача в заявке <a href="${link.getRecordLink(docRef)}" target="_blank">SD-${docNum}</a>.</p>
<p>Предыдущий статус заявки: <i>${stat}</i>.</p>

<@lib.reply_message/>
