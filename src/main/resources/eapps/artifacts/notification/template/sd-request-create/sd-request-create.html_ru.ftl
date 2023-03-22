<#import "sd-template-lib" as lib>

<p>Уважаемый (-ая) ${initiator}!</p>
<p>Вашему запросу присвоен номер <a href="${link.getRecordLink(docRef)}" target="_blank">SD-${docNum}</a>.</p>
<p>В ближайшее время мы приступим к работе по Вашему обращению.</p>

<@lib.reply_message/>
