@php($bn = request()->query('lang') === 'bn')
@extends('errors.layout')

@section('code', '500')
@section('eyebrow', $bn ? 'সার্ভার অনুরোধটি সম্পন্ন করতে পারেনি' : 'The server could not complete the request')
@section('title', $bn ? 'সার্ভার সমস্যা' : 'Server error')
@section('message', $bn
    ? 'SAFA একটি অপ্রত্যাশিত server error পেয়েছে। কোনো credential বা private setup code এই পেইজে দেখানো হয় না। আবার চেষ্টা করুন; সমস্যা থাকলে server log পরীক্ষা করুন।'
    : 'SAFA encountered an unexpected server error. No credentials or private setup code are exposed on this page. Retry the request, and check the server log if the problem continues.')
