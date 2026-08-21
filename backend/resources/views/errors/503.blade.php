@php($bn = request()->query('lang') === 'bn')
@extends('errors.layout')

@section('code', '503')
@section('eyebrow', $bn ? 'সার্ভিসটি সাময়িকভাবে প্রস্তুত নয়' : 'The service is temporarily not ready')
@section('title', $bn ? 'সার্ভিস অনুপলব্ধ' : 'Service unavailable')
@section('message', $bn
    ? 'Server maintenance, database update বা first-run setup-এর কারণে SAFA সাময়িকভাবে অনুপলব্ধ হতে পারে। SAFA হোমে গেলে প্রয়োজনীয় setup/update flow দেখানো হবে।'
    : 'SAFA may be temporarily unavailable because of server maintenance, a database update, or first-run setup. Returning to SAFA home will show the required setup/update flow.')
