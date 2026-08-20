<?php

namespace Tests\Feature;

use App\Support\CredentialVerifier;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class CredentialVerifierTest extends TestCase
{
    public function test_missing_identity_executes_two_dummy_bcrypt_checks(): void
    {
        Hash::shouldReceive('check')
            ->twice()
            ->with('654321', CredentialVerifier::DUMMY_BCRYPT_HASH)
            ->andReturn(false);

        $this->assertFalse(CredentialVerifier::verify('654321', [null, null]));
    }
}
