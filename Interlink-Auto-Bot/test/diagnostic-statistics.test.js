const {test}=require('node:test');const assert=require('node:assert/strict');const {analyze}=require('../tools/analyze-diagnostics');
test('statistics count unique due slots, exclude cancellations/probes and never count recovery as delivery',()=>{
 const event=(event,id,at,actualAt=at)=>JSON.stringify({event,executionId:id,scheduledAt:at,actualAt});
 const text=[event('ALARM_SCHEDULED','schedule-10',10),event('ALARM_SCHEDULED','schedule-10',10),event('ALARM_RECEIVED','schedule-10',10,42),
 event('ALARM_SCHEDULED','schedule-20',20),event('RECOVERY_ATTEMPTED','schedule-20',20,30),
 event('ALARM_SCHEDULED','schedule-30',30),event('ALARM_CANCELLED','schedule-30',30),
 event('ALARM_SCHEDULED','schedule-200',200),event('ALARM_SCHEDULED','test-1',1), 'human header'].join('\n');
 const stats=analyze(text,100);assert.equal(stats.dueRegistered,2);assert.equal(stats.delivered,1);assert.equal(stats.withoutDeliveryEvidence,1);assert.equal(stats.latencyMs.mean,32);
});
