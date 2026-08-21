@php($bn = request()->query('lang') === 'bn')
@extends('errors.layout')

@section('code', '401')
@section('eyebrow', $bn ? 'লগইন বা session যাচাই প্রয়োজন' : 'Sign-in or session verification is required')
@section('title', $bn ? 'অনুমোদন প্রয়োজন' : 'Authentication required')
@section('message', $bn
    ? 'এই পেইজ ব্যবহার করতে বৈধ SAFA session দরকার। আবার লগইন করুন। যদি server first-run setup চায়, SAFA হোম আপনাকে setup পেইজে নিয়ে যাবে।'
    : 'A valid SAFA session is required to use this page. Sign in again. If the server requires first-run setup, SAFA home will route you to the setup page.')
