@php($bn = request()->query('lang') === 'bn')
@extends('errors.layout')

@section('code', '403')
@section('eyebrow', $bn ? 'এই কাজের অনুমতি নেই' : 'This action is not authorized')
@section('title', $bn ? 'অ্যাক্সেস নিষিদ্ধ' : 'Access forbidden')
@section('message', $bn
    ? 'আপনার বর্তমান account, role বা setup session এই পেইজ/কাজটির অনুমতি দেয় না। সঠিক account দিয়ে লগইন করুন অথবা first-run setup যেই browser session শুরু করেছে সেটি ব্যবহার করুন।'
    : 'Your current account, role, or setup session is not allowed to access this page or action. Sign in with the correct account, or use the browser session that started first-run setup.')
