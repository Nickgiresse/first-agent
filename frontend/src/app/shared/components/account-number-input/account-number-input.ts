import { Component, ElementRef, EventEmitter, Input, Output, ViewChild, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

@Component({
  selector: 'afb-account-number-input',
  standalone: true,
  templateUrl: './account-number-input.html',
  styleUrl: './account-number-input.scss',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => AccountNumberInput),
      multi: true
    }
  ]
})
export class AccountNumberInput implements ControlValueAccessor {
  @Input() placeholder = '12345 67890123456 78';
  @Input() disabled = false;
  @Input() set value(val: string | null) {
    this.writeValue(val);
  }
  @Output() valueChange = new EventEmitter<string>();
  /**
   * Perte de focus du champ.
   *
   * Nommée `blurred` et non `blur` : une sortie portant le nom d'un événement
   * DOM standard le masque. Un parent écrivant `(blur)` sur ce composant
   * recevrait cette sortie et non l'événement du navigateur, sans que rien ne
   * signale la substitution.
   */
  @Output() blurred = new EventEmitter<void>();

  @ViewChild('inputRef') inputRef!: ElementRef<HTMLInputElement>;

  rawDigits = '';
  formattedValue = '';

  private onChange: (val: string) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(val: string | null): void {
    const raw = (val || '').replace(/\D/g, '').slice(0, 18);
    if (raw !== this.rawDigits) {
      this.rawDigits = raw;
      this.formattedValue = this.format(raw);
      if (this.inputRef?.nativeElement) {
        this.inputRef.nativeElement.value = this.formattedValue;
      }
    }
  }

  registerOnChange(fn: (val: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  format(digits: string): string {
    const clean = digits.replace(/\D/g, '').slice(0, 18);
    if (!clean) return '';
    const groupSizes = [5, 11, 2];
    const chunks: string[] = [];
    let idx = 0;
    for (const size of groupSizes) {
      if (idx >= clean.length) break;
      chunks.push(clean.slice(idx, idx + size));
      idx += size;
    }
    return chunks.join(' ');
  }

  onInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const selectionStart = input.selectionStart || 0;
    const oldVal = input.value;

    const digitsBeforeCursor = oldVal.slice(0, selectionStart).replace(/\D/g, '').length;

    const newRaw = input.value.replace(/\D/g, '').slice(0, 18);
    this.rawDigits = newRaw;
    this.formattedValue = this.format(newRaw);
    input.value = this.formattedValue;

    let newCursorPos = this.formattedValue.length;
    let digitCount = 0;
    for (let i = 0; i < this.formattedValue.length; i++) {
      if (/\d/.test(this.formattedValue[i])) {
        digitCount++;
      }
      if (digitCount >= digitsBeforeCursor) {
        newCursorPos = i + 1;
        break;
      }
    }
    if (digitsBeforeCursor === 0) {
      newCursorPos = 0;
    }

    input.setSelectionRange(newCursorPos, newCursorPos);

    this.onChange(this.rawDigits);
    this.valueChange.emit(this.rawDigits);
  }

  onPaste(event: ClipboardEvent): void {
    event.preventDefault();
    const pastedText = event.clipboardData?.getData('text') || '';
    const cleanPasted = pastedText.replace(/\D/g, '');
    if (!cleanPasted) return;

    const input = this.inputRef?.nativeElement;
    const selStart = input?.selectionStart || 0;
    const selEnd = input?.selectionEnd || 0;

    const currentRaw = this.rawDigits;
    const digitsBefore = this.formattedValue.slice(0, selStart).replace(/\D/g, '').length;
    const digitsSelected = this.formattedValue.slice(selStart, selEnd).replace(/\D/g, '').length;

    const left = currentRaw.slice(0, digitsBefore);
    const right = currentRaw.slice(digitsBefore + digitsSelected);

    const updatedRaw = (left + cleanPasted + right).slice(0, 18);
    this.rawDigits = updatedRaw;
    this.formattedValue = this.format(updatedRaw);

    if (input) {
      input.value = this.formattedValue;
      const targetDigitIndex = left.length + cleanPasted.length;
      let newCursorPos = this.formattedValue.length;
      let count = 0;
      for (let i = 0; i < this.formattedValue.length; i++) {
        if (/\d/.test(this.formattedValue[i])) {
          count++;
        }
        if (count >= targetDigitIndex) {
          newCursorPos = i + 1;
          break;
        }
      }
      input.setSelectionRange(newCursorPos, newCursorPos);
    }

    this.onChange(this.rawDigits);
    this.valueChange.emit(this.rawDigits);
  }

  handleBlur(): void {
    this.onTouched();
    this.blurred.emit();
  }
}
