import { expect, test } from '@playwright/test';
import { AdminApi, uniqueName } from '../support/api';
import { pickOption } from '../support/select';
import { openAdminPage } from '../support/session';

test.afterAll(async () => {
  const api = await AdminApi.create();
  try {
    await api.cleanUp();
  } finally {
    await api.dispose();
  }
});

test('the admin builds a product with a modifier group', async ({ browser }) => {
  const categoryName = uniqueName('category');
  const groupName = uniqueName('group');
  const productName = uniqueName('product');
  const page = await openAdminPage(browser, '/admin/catalog/categories');

  await test.step('create the category', async () => {
    await page.getByLabel('Nombre de la categoría').fill(categoryName);
    await page.getByRole('button', { name: 'Agregar categoría' }).click();
    const row = page.getByRole('row', { name: new RegExp(categoryName) });
    await expect(row).toContainText('Activa');
  });

  await test.step('create the modifier group with its options and rules', async () => {
    await page.goto('/admin/catalog/modifier-groups/new');
    await page.getByLabel('Nombre del grupo').fill(groupName);
    await page.getByLabel('Elección obligatoria').check();
    await page.getByLabel('Máximo de opciones').fill('2');
    await page.getByRole('button', { name: 'Agregar opción' }).click();
    await page.getByRole('button', { name: 'Agregar opción' }).click();
    const options = [
      ['Sin azúcar', '0.00'],
      ['Con miel', '1.50'],
      ['Con stevia', '1.00'],
    ] as const;
    for (const [index, [name, price]] of options.entries()) {
      const option = page.getByRole('group', { name: `Opción ${index + 1}` });
      await option.getByLabel('Nombre de la opción').fill(name);
      await option.getByLabel(/Precio adicional/).fill(price);
    }
    await page.getByRole('button', { name: 'Guardar grupo' }).click();

    const row = page.getByRole('row', { name: new RegExp(groupName) });
    await expect(row).toContainText('Obligatorio');
    await expect(row).toContainText('1 a 2');
    await expect(row).toContainText('3 opciones');
  });

  await test.step('create the product in that category with the group attached', async () => {
    await page.goto('/admin/catalog/products/new');
    await page.getByLabel('Nombre', { exact: true }).fill(productName);
    await pickOption(page, 'Categoría', categoryName);
    await page.getByLabel(/^Precio/).fill('12.50');
    await pickOption(page, 'Grupo a agregar', groupName);
    await page.getByRole('button', { name: 'Agregar grupo' }).click();
    await expect(page.getByRole('listitem').filter({ hasText: groupName })).toBeVisible();
    await page.getByRole('button', { name: 'Guardar producto' }).click();

    await expect(page.getByRole('heading', { name: 'Editar producto' })).toBeVisible();
    await expect(page.getByRole('listitem').filter({ hasText: groupName })).toBeVisible();
  });

  await test.step('the product is listed under its category', async () => {
    await page.getByRole('link', { name: 'Volver a la lista' }).click();
    await pickOption(page, 'Categoría', categoryName);
    const row = page.getByRole('row', { name: new RegExp(productName) });
    await expect(row).toContainText(categoryName);
    await expect(row).toContainText('Activo');
  });

  await test.step('the saved product still shows the attached group', async () => {
    await page.getByRole('row', { name: new RegExp(productName) }).getByRole('link', { name: 'Editar' }).click();
    await expect(page.getByRole('heading', { name: 'Editar producto' })).toBeVisible();
    await expect(page.getByRole('listitem').filter({ hasText: groupName })).toBeVisible();
  });

  await page.context().close();
});
