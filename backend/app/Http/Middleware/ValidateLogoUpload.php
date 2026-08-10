<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class ValidateLogoUpload
{
    private const MAX_BYTES = 2_097_152; // 2 MiB
    private const ALLOWED_MIMES = ['image/png', 'image/jpeg', 'image/webp', 'image/gif'];

    public function handle(Request $request, Closure $next): Response
    {
        foreach (['logo', 'image', 'file'] as $field) {
            if ($request->hasFile($field)) {
                $file = $request->file($field);
                if (!$file || !$file->isValid() || (int) $file->getSize() > self::MAX_BYTES) {
                    return response()->json(['status' => 'error', 'message' => 'Invalid or oversized logo file. Maximum size is 2 MB.'], 422);
                }
                if (!in_array((string) $file->getMimeType(), self::ALLOWED_MIMES, true)) {
                    return response()->json(['status' => 'error', 'message' => 'Unsupported logo image type.'], 422);
                }
                if (@getimagesize($file->getRealPath()) === false) {
                    return response()->json(['status' => 'error', 'message' => 'Uploaded logo is not a valid image.'], 422);
                }
                return $next($request);
            }
        }

        $base64 = $request->input('logo') ?? $request->input('image') ?? $request->input('base64') ?? $request->input('logo_base64');
        if (!is_string($base64) || $base64 === '') {
            return response()->json(['status' => 'error', 'message' => 'No logo image provided.'], 422);
        }

        if (strlen($base64) > 3_000_000) {
            return response()->json(['status' => 'error', 'message' => 'Logo payload is too large. Maximum size is 2 MB.'], 422);
        }

        if (preg_match('/^data:image\/(png|jpe?g|webp|gif);base64,/i', $base64, $match)) {
            $base64 = substr($base64, strpos($base64, ',') + 1);
        }

        $decoded = base64_decode($base64, true);
        if ($decoded === false || strlen($decoded) > self::MAX_BYTES || @getimagesizefromstring($decoded) === false) {
            return response()->json(['status' => 'error', 'message' => 'Invalid or oversized base64 logo image.'], 422);
        }

        return $next($request);
    }
}
