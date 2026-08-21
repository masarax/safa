@php($bn = request()->query('lang') === 'bn')
@extends('errors.layout')

@section('code', '419')
@section('eyebrow', $bn ? 'নিরাপত্তা token/session আর বৈধ নেই' : 'The security token or session is no longer valid')
@section('title', $bn ? 'পেইজের মেয়াদ শেষ' : 'Page expired')
@section('message', $bn
    ? 'নিরাপত্তার জন্য পুরোনো form আর submit করা যাবে না। পেইজটি নতুন করে খুলে আবার চেষ্টা করুন।'
    : 'For security, an old form can no longer be submitted. Reload the page and try the action again.')
