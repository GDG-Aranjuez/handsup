<script lang="ts">
  import QRCode from 'qrcode';

  let { value, label = '', size = 192 }: { value: string; label?: string; size?: number } = $props();
  let svg = $state('');

  $effect(() => {
    let live = true;
    QRCode.toString(value, { type: 'svg', margin: 1, errorCorrectionLevel: 'M', color: { dark: '#14141a', light: '#ffffff' } })
      .then((s) => {
        if (live) svg = s;
      })
      .catch(() => (svg = ''));
    return () => (live = false);
  });
</script>

<div
  class="inline-block overflow-hidden rounded-xl bg-white p-1 [&>svg]:h-full [&>svg]:w-full"
  style="width:{size}px;height:{size}px"
  role="img"
  aria-label={label}
  data-testid="qr"
>
  {@html svg}
</div>
