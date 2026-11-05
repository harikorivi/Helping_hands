const { test } = require('node:test');
const assert = require('node:assert/strict');
const { parseEligibility, responseShape } = require('../lib/eligibility');
test('eligibility accepts the original nested boolean without application status', () => {
  for (const isClaimable of [true,false]) {
    assert.equal(parseEligibility({status:200,data:{data:{isClaimable}}}),isClaimable);
    assert.equal(parseEligibility({status:200,data:{statusCode:200,data:{isClaimable}}}),isClaimable);
  }
});
test('eligibility fails closed for explicit failure, missing transport, and malformed values', () => {
  for (const response of [
    {status:401,data:{data:{isClaimable:true}}},
    {status:200,data:{statusCode:500,data:{isClaimable:true}}},
    {status:200,data:{statusCode:null,data:{isClaimable:true}}},
    {data:{data:{isClaimable:true}}},
    {status:200,data:{data:{isClaimable:'true'}}},
    {status:200,data:{isClaimable:true}},
    {status:200,data:'html'}
  ]) assert.throws(()=>parseEligibility(response));
});
test('shape diagnostics contain only known types and numeric statuses', () => {
  const shape=responseShape({status:200,data:{statusCode:'secret',data:{isClaimable:'secret',token:'secret'}}});
  assert.equal(shape.applicationStatusType,'string');
  assert.equal(shape.nestedEligibilityType,'string');
  assert.ok(!JSON.stringify(shape).includes('secret'));
});
