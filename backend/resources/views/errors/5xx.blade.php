@php
    $bn = request()->query('lang') === 'bn';
    $code = isset($exception) && method_exists($exception, 'getStatusCode') ? (string) $exception->getStatusCode() : '5xx';
@endphp
@extends('errors.layout')

@section('code', $code)
@section('eyebrow', $bn ? 'সার্ভার অনুরোধটি সম্পন্ন করতে পারেনি' : 'The server could not complete the request')
@section('title', $bn ? 'সার্ভার সমস্যা' : 'Server problem')
@section('message', $bn
    ? 'SAFA server একটি অপ্রত্যাশিত সমস্যার সম্মুখীন হয়েছে। আবার চেষ্টা করুন; সমস্যা থাকলে server log ও deployment state পরীক্ষা করুন।'
    : 'The SAFA server encountered an unexpected problem. Retry the request; if it continues, check the server log and deployment state.')
