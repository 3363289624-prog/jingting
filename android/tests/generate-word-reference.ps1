Add-Type -AssemblyName System.Speech
$referenceSentences = @(
  'The international organization made an important announcement.',
  'It is a beautiful opportunity to learn something new.',
  'She has been working at the university for several years.',
  'Take a moment, breathe slowly, and try again.',
  'I would like to understand the difference between these ideas.',
  'The development of technology can change our daily lives.',
  'We should consider the environmental consequences carefully.',
  'Please listen to the conversation and answer the question.',
  'Although it was raining, they decided to continue their journey.',
  'This is an interesting example of communication.',
  'He said, "Wait a minute. What do you mean?"',
  'You can improve your pronunciation with regular practice.',
  'A little progress every day is better than doing nothing.',
  'The students discussed their research with the professor.',
  'I have never seen such an extraordinary performance.',
  'If you need any assistance, please let me know.',
  'Some people prefer a quiet place to study.',
  'We cannot predict exactly what will happen tomorrow.',
  'The information is available on the official website.',
  'Do you remember where you put the blue notebook?',
  'Our responsibility is to make education accessible to everyone.',
  'Please bring a comfortable pair of shoes for the trip.',
  'After a short introduction, the speaker explained the experiment.',
  'There is a considerable difference in their expectations.',
  'You should not make an important decision in a hurry.',
  'The museum is open every day except Monday.',
  'Could you explain the situation a little more clearly?',
  'The new transportation system will connect the two cities.',
  'It took considerable patience to solve the complicated problem.',
  'First, check the details; then, submit the application.',
  'Dr. Smith has worked for the BBC since 2020.',
  'The ticket costs $25.50, including a 10 percent service charge.',
  'I cannot believe that you have already finished the assignment.',
  'They are going to visit the library this afternoon.',
  'She did not recognize the unfamiliar expression.',
  'We need to find a reasonable solution to this problem.'
)
$referenceRoot = Join-Path $PSScriptRoot 'speech'
New-Item -ItemType Directory -Force -Path $referenceRoot | Out-Null
$synthReference = [System.Speech.Synthesis.SpeechSynthesizer]::new()
$synthReference.SelectVoice('Microsoft Zira Desktop')
# A fixed 16 kHz format is needed so event AudioPosition uses the same timebase.
$referenceFormat = [System.Speech.AudioFormat.SpeechAudioFormatInfo]::new(16000,[System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,[System.Speech.AudioFormat.AudioChannel]::Mono)
$referenceSubscription = Register-ObjectEvent -InputObject $synthReference -EventName SpeakProgress -SourceIdentifier 'JingtingTimingReference'
$referenceRows = [System.Collections.Generic.List[object]]::new()
try {
  for ($referenceIndex=0; $referenceIndex -lt $referenceSentences.Count; $referenceIndex++) {
    Remove-Event -SourceIdentifier 'JingtingTimingReference' -ErrorAction SilentlyContinue
    $synthReference.Rate = 0
    $waveName = 'reference-{0:d2}.wav' -f $referenceIndex
    $synthReference.SetOutputToWaveFile((Join-Path $referenceRoot $waveName),$referenceFormat)
    $synthReference.Speak($referenceSentences[$referenceIndex])
    $synthReference.SetOutputToNull()
    $words = @(Get-Event -SourceIdentifier 'JingtingTimingReference' | ForEach-Object {
      $argsReference = $_.SourceEventArgs
      [pscustomobject]@{text=$argsReference.Text;at=$argsReference.CharacterPosition;length=$argsReference.CharacterCount;start=$argsReference.AudioPosition.TotalSeconds}
    })
    $referenceRows.Add([pscustomobject]@{text=$referenceSentences[$referenceIndex];file=$waveName;split=$(if($referenceIndex % 3 -eq 2){'holdout'}else{'development'});words=$words})
  }
  ConvertTo-Json -InputObject @($referenceRows.ToArray()) -Depth 8 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'reference.json') -Encoding utf8
  Write-Output ('Generated {0} local speech references.' -f $referenceRows.Count)
} finally {
  Unregister-Event -SourceIdentifier 'JingtingTimingReference' -ErrorAction SilentlyContinue
  Remove-Event -SourceIdentifier 'JingtingTimingReference' -ErrorAction SilentlyContinue
  $synthReference.Dispose()
}
