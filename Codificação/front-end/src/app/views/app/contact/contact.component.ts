import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ContactService } from '../../../services/contact.service';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-contact',
  imports: [RouterModule, CommonModule, ReactiveFormsModule],
  templateUrl: './contact.component.html',
  styleUrl: './contact.component.css'
})
export class ContactComponent implements OnInit {
  form!: FormGroup;
  loading = false;

  constructor(
    private fb: FormBuilder,
    private contactService: ContactService,
    private toastr: ToastrService
  ) {}

  ngOnInit() {
    // Logado: nome/e-mail vêm da conta e não podem ser alterados.
    // Visitante (RF08): campos livres para ele se identificar.
    const logged = !!localStorage.getItem('token');
    this.form = this.fb.group({
      name:    [{ value: localStorage.getItem('fullname') || '', disabled: logged }, [Validators.required, Validators.minLength(2)]],
      email:   [{ value: localStorage.getItem('email') || '', disabled: logged }, [Validators.required, Validators.email]],
      subject: [''],
      message: ['', [Validators.required, Validators.minLength(10)]]
    });
  }

  async send() {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading = true;
    try {
      await this.contactService.send(this.form.getRawValue());
      this.toastr.success('Mensagem enviada! Em breve a administração entrará em contato.');
      this.form.reset({
        name: localStorage.getItem('fullname') || '',
        email: localStorage.getItem('email') || ''
      });
    } catch {
      this.toastr.error('Não foi possível enviar a mensagem. Tente novamente.');
    } finally {
      this.loading = false;
    }
  }
}
