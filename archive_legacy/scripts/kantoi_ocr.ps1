param([string]$ImagePath)

Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTaskGeneric = [System.WindowsRuntimeSystemExtensions].GetMethods() | ? { 
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' 
}

function Await($WinRtTask, $ResultType) {
    $asTask = $asTaskGeneric.MakeGenericMethod($ResultType)
    $netTask = $asTask.Invoke($null, @($WinRtTask))
    $netTask.Wait(-1) | Out-Null
    $netTask.Result
}

[Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.Ocr.OcrEngine, Windows.Foundation, ContentType = WindowsRuntime] | Out-Null
[Windows.Graphics.Imaging.BitmapDecoder, Windows.Graphics.Imaging, ContentType = WindowsRuntime] | Out-Null

$fullPath = [System.IO.Path]::GetFullPath($ImagePath)
if (-not (Test-Path $fullPath)) {
    Write-Error "File not found: $fullPath"
    exit 1
}

$fileOp = [Windows.Storage.StorageFile]::GetFileFromPathAsync($fullPath)
$file = Await $fileOp ([Windows.Storage.StorageFile])

$streamOp = $file.OpenAsync([Windows.Storage.FileAccessMode]::Read)
$stream = Await $streamOp ([Windows.Storage.Streams.IRandomAccessStream])

$decoderOp = [Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream)
$decoder = Await $decoderOp ([Windows.Graphics.Imaging.BitmapDecoder])

$bitmapOp = $decoder.GetSoftwareBitmapAsync()
$bitmap = Await $bitmapOp ([Windows.Graphics.Imaging.SoftwareBitmap])

$engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages()
if ($null -eq $engine) {
    $lang = [Windows.Globalization.Language]::new("en-US")
    $engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromLanguage($lang)
}

$ocrOp = $engine.RecognizeAsync($bitmap)
$ocrResult = Await $ocrOp ([Windows.Media.Ocr.OcrResult])

$lines = $ocrResult.Lines | ForEach-Object { $_.Text }
$lines -join "`n"
