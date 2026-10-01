import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

const steps = [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950] as const;

// The palette lives in the global stylesheet as CSS variables, so PrimeNG and app tokens share one source.
function scale(name: 'primary' | 'surface', extra: readonly number[] = []): Record<number, string> {
  return Object.fromEntries(
    [...extra, ...steps].map((step) => [step, `var(--palette-${name}-${step})`]),
  );
}

export const AppPreset = definePreset(Aura, {
  semantic: {
    primary: scale('primary'),
    colorScheme: {
      light: {
        surface: scale('surface', [0]),
        primary: {
          color: '{primary.700}',
          contrastColor: '#ffffff',
          hoverColor: '{primary.800}',
          activeColor: '{primary.900}',
        },
      },
      dark: {
        surface: scale('surface', [0]),
        primary: {
          color: '{primary.400}',
          contrastColor: '{surface.950}',
          hoverColor: '{primary.300}',
          activeColor: '{primary.200}',
        },
      },
    },
  },
});
