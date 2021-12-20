/*
 * Copyright  2021 LSD Information Technology (Pty) Ltd
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

import {Component, OnInit} from '@angular/core';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {DuplicateOptions, Release} from '../release';

@Component({
  selector: 'app-duplicate-dialog',
  templateUrl: './duplicate-dialog.component.html',
  styleUrls: ['./duplicate-dialog.component.scss']
})
export class DuplicateDialogComponent implements OnInit {
  selectedRelease: Release;
  duplicateOptions = new DuplicateOptions();

  constructor(public ref: DynamicDialogRef,
              public config: DynamicDialogConfig) {
    const data = config.data;
    this.selectedRelease = data.selectedRelease;
  }

  ngOnInit(): void {
  }

  close(result: any) {
    this.ref.close(result);
  }
}
