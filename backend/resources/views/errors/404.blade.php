@php($bn = request()->query('lang') === 'bn')
@extends('errors.layout')

@section('code', '404')
@section('eyebrow', $bn ? 'এই ঠিকানায় কোনো পেইজ নেই' : 'No page exists at this address')
@section('title', $bn ? 'পেইজ পাওয়া যায়নি' : 'Page not found')
@section('message', $bn
    ? 'আপনি যে পেইজটি খুলতে চেয়েছেন সেটি নেই, সরানো হয়েছে, অথবা এই installation state-এ ব্যবহারযোগ্য নয়। SAFA হোমে ফিরে গেলে প্রয়োজন হলে first-run setup স্বয়ংক্রিয়ভাবে দেখানো হবে।'
    : 'The page you requested does not exist, was moved, or is unavailable in the current installation state. Returning to SAFA home will automatically show first-run setup when it is required.')
