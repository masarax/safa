@php($bn = request()->query('lang') === 'bn')
@extends('errors.layout')

@section('code', '429')
@section('eyebrow', $bn ? 'খুব বেশি অনুরোধ পাঠানো হয়েছে' : 'Too many requests were sent')
@section('title', $bn ? 'কিছুক্ষণ পরে আবার চেষ্টা করুন' : 'Try again shortly')
@section('message', $bn
    ? 'SAFA নিরাপত্তা ও স্থিতিশীলতার জন্য অস্থায়ীভাবে request limit প্রয়োগ করেছে। কিছুক্ষণ অপেক্ষা করে আবার চেষ্টা করুন।'
    : 'SAFA temporarily applied a request limit for security and stability. Wait briefly, then try again.')
