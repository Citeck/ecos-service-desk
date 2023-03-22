<#import "sd-template-lib" as lib>

<p>Добрый день!</p>
<p>Мы взяли обращение <a href="${link.getRecordLink(docRef)}" target="_blank">SD-${docNum}</a> в работу.</p>
<p>В ближайшее время сообщим о результатах.</p>

<@lib.reply_message/>
