@php
    $bn = request()->query('lang') === 'bn';
    $code = isset($exception) && method_exists($exception, 'getStatusCode') ? (string) $exception->getStatusCode() : '4xx';
@endphp
@extends('errors.layout')

@section('code', $code)
@section('eyebrow', $bn ? 'অনুরোধটি এই অবস্থায় গ্রহণ করা যায়নি' : 'The request cannot be accepted in its current state')
@section('title', $bn ? 'অনুরোধ সম্পন্ন হয়নি' : 'Request could not be completed')
@section('message', $bn
    ? 'পেইজ, session, permission বা request method-এর কারণে অনুরোধটি সম্পন্ন হয়নি। SAFA হোম থেকে সঠিক flow আবার শুরু করুন।'
    : 'The request could not be completed because of the page, session, permission, or request method. Restart the correct flow from SAFA home.')
