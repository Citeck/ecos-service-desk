<#import "sd-template-lib" as lib>

<p>Добрый день!</p>
<p>Истекает срок SLA 2. Необходимо принять решение по заявке <a href="${link.getRecordLink(docRef)}"
                                                                target="_blank">SD-${docNum}</a>.</p>

<@lib.reply_message/>
